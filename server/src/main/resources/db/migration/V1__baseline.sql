CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9_]{3,32}$'),
    display_name VARCHAR(120) NOT NULL CHECK (length(trim(display_name)) > 0),
    password_hash VARCHAR(255) NOT NULL,
    zone_id VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sessions (
    token_hash BYTEA PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX sessions_user_id_idx ON sessions(user_id);

CREATE TABLE households (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    zone_id VARCHAR(120) NOT NULL DEFAULT 'Asia/Shanghai',
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(6) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX memberships_one_active_household ON memberships(user_id) WHERE active;
CREATE UNIQUE INDEX memberships_one_active_owner ON memberships(household_id) WHERE active AND role = 'OWNER';
CREATE INDEX memberships_household_idx ON memberships(household_id, active);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    created_by UUID NOT NULL REFERENCES users(id),
    code_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(code_hash) = 32),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX invitations_household_idx ON invitations(household_id);

CREATE TABLE ingredients (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    unit VARCHAR(5) NOT NULL CHECK (unit IN ('G', 'ML', 'COUNT')),
    active BOOLEAN NOT NULL,
    UNIQUE (id, unit)
);
CREATE TABLE ingredient_aliases (
    normalized_name TEXT PRIMARY KEY,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id),
    display_name VARCHAR(120) NOT NULL CHECK (length(trim(display_name)) > 0)
);
CREATE INDEX ingredient_aliases_ingredient_idx ON ingredient_aliases(ingredient_id);

CREATE TABLE preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    liked_tags TEXT[] NOT NULL DEFAULT '{}',
    excluded_ingredient_ids UUID[] NOT NULL DEFAULT '{}',
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);

CREATE TABLE media (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    household_id UUID REFERENCES households(id),
    scope VARCHAR(9) NOT NULL CHECK (scope IN ('PERSONAL', 'HOUSEHOLD')),
    mime_type VARCHAR(20) NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    storage_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((scope = 'PERSONAL' AND household_id IS NULL) OR (scope = 'HOUSEHOLD' AND household_id IS NOT NULL))
);
CREATE INDEX media_owner_idx ON media(owner_user_id);
CREATE INDEX media_household_idx ON media(household_id);

CREATE TABLE recipes (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    author_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    steps JSONB NOT NULL CHECK (jsonb_typeof(steps) = 'array' AND jsonb_array_length(steps) >= 1),
    yield_servings NUMERIC(18,3) NOT NULL CHECK (yield_servings > 0),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    tags TEXT[] NOT NULL DEFAULT '{}',
    image_id UUID REFERENCES media(id),
    total_kcal NUMERIC(18,3) CHECK (total_kcal >= 0),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, household_id)
);
CREATE INDEX recipes_household_created_idx ON recipes(household_id, created_at DESC, id);
CREATE TABLE recipe_ingredients (
    recipe_id UUID NOT NULL REFERENCES recipes(id),
    ingredient_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(5) NOT NULL,
    track_stock BOOLEAN NOT NULL,
    PRIMARY KEY (recipe_id, ingredient_id),
    UNIQUE (recipe_id, position),
    FOREIGN KEY (ingredient_id, unit) REFERENCES ingredients(id, unit)
);
CREATE INDEX recipe_ingredients_ingredient_idx ON recipe_ingredients(ingredient_id);
CREATE TABLE wishlists (
    user_id UUID NOT NULL REFERENCES users(id),
    recipe_id UUID NOT NULL REFERENCES recipes(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, recipe_id)
);
CREATE INDEX wishlists_recipe_idx ON wishlists(recipe_id);

CREATE TABLE inventory_batches (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    ingredient_id UUID NOT NULL,
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity >= 0),
    unit VARCHAR(5) NOT NULL,
    location VARCHAR(120) NOT NULL,
    expires_on DATE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ingredient_id, unit) REFERENCES ingredients(id, unit),
    UNIQUE (id, household_id)
);
CREATE INDEX inventory_batches_match_idx ON inventory_batches(household_id, ingredient_id, expires_on, created_at, id);

CREATE TABLE plan_slots (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    date DATE NOT NULL,
    meal_type VARCHAR(9) NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (household_id, date, meal_type),
    UNIQUE (id, household_id)
);
CREATE INDEX plan_slots_household_date_idx ON plan_slots(household_id, date);
CREATE TABLE plan_items (
    slot_id UUID NOT NULL,
    household_id UUID NOT NULL,
    recipe_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 9),
    servings NUMERIC(18,3) NOT NULL CHECK (servings > 0),
    PRIMARY KEY (slot_id, recipe_id),
    UNIQUE (slot_id, position),
    FOREIGN KEY (slot_id, household_id) REFERENCES plan_slots(id, household_id),
    FOREIGN KEY (recipe_id, household_id) REFERENCES recipes(id, household_id)
);
CREATE INDEX plan_items_recipe_idx ON plan_items(recipe_id);

CREATE TABLE cooking_events (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    cooked_at TIMESTAMPTZ NOT NULL,
    servings NUMERIC(18,3) NOT NULL CHECK (servings > 0),
    recipe_id UUID NOT NULL,
    recipe_snapshot JSONB NOT NULL CHECK (jsonb_typeof(recipe_snapshot) = 'object'),
    plan_slot_id UUID,
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipe_id, household_id) REFERENCES recipes(id, household_id),
    FOREIGN KEY (plan_slot_id, household_id) REFERENCES plan_slots(id, household_id),
    UNIQUE (id, household_id)
);
CREATE INDEX cooking_events_household_time_idx ON cooking_events(household_id, cooked_at DESC, id);
CREATE INDEX cooking_events_recipe_idx ON cooking_events(recipe_id);
CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    batch_id UUID NOT NULL,
    before_quantity NUMERIC(18,3) NOT NULL CHECK (before_quantity >= 0),
    after_quantity NUMERIC(18,3) NOT NULL CHECK (after_quantity >= 0),
    reason VARCHAR(7) NOT NULL CHECK (reason IN ('COOK', 'CONSUME', 'DISCARD', 'CORRECT')),
    actor_id UUID NOT NULL REFERENCES users(id),
    cooking_event_id UUID,
    request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id, household_id) REFERENCES inventory_batches(id, household_id),
    FOREIGN KEY (cooking_event_id, household_id) REFERENCES cooking_events(id, household_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX inventory_movements_batch_idx ON inventory_movements(batch_id, created_at DESC, id);
CREATE INDEX inventory_movements_household_idx ON inventory_movements(household_id, created_at DESC, id);

CREATE TABLE meal_entries (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    zone_id VARCHAR(120) NOT NULL,
    meal_type VARCHAR(9) NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    recipe_id UUID REFERENCES recipes(id),
    cooking_event_id UUID REFERENCES cooking_events(id),
    amount NUMERIC(18,3) NOT NULL CHECK (amount > 0),
    basis VARCHAR(7) NOT NULL CHECK (basis IN ('SERVING', 'GRAM', 'MANUAL', 'UNKNOWN')),
    basis_kcal NUMERIC(18,3) CHECK (basis_kcal >= 0),
    total_kcal NUMERIC(18,3) CHECK (total_kcal >= 0),
    note VARCHAR(2000) NOT NULL,
    image_id UUID REFERENCES media(id),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((basis = 'UNKNOWN' AND basis_kcal IS NULL AND total_kcal IS NULL)
        OR (basis <> 'UNKNOWN' AND basis_kcal IS NOT NULL AND total_kcal IS NOT NULL)),
    CHECK (basis <> 'MANUAL' OR amount = 1)
);
CREATE INDEX meal_entries_user_date_idx ON meal_entries(user_id, date DESC, created_at DESC, id);
CREATE INDEX meal_entries_recipe_idx ON meal_entries(recipe_id);
CREATE INDEX meal_entries_cooking_event_idx ON meal_entries(cooking_event_id);

CREATE TABLE idempotency_requests (
    actor_id UUID NOT NULL REFERENCES users(id),
    key UUID NOT NULL,
    operation_digest BYTEA NOT NULL CHECK (octet_length(operation_digest) = 32),
    http_status INTEGER CHECK (http_status BETWEEN 200 AND 299),
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_id, key),
    CHECK (http_status IS NULL OR http_status = 204 OR response_body IS NOT NULL)
);

-- A placeholder may exist within a transaction, but may never be committed incomplete.
CREATE FUNCTION civa_require_completed_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM idempotency_requests WHERE actor_id = NEW.actor_id AND key = NEW.key AND http_status IS NULL) THEN
        RAISE EXCEPTION 'idempotency request must be completed before commit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER idempotency_request_completed
    AFTER INSERT OR UPDATE ON idempotency_requests DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION civa_require_completed_idempotency();

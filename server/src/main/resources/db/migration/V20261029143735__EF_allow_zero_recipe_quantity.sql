-- Wire quantities permit zero; only servings and consumed amounts require positivity.
ALTER TABLE recipe_ingredients DROP CONSTRAINT recipe_ingredients_quantity_check;
ALTER TABLE recipe_ingredients ADD CONSTRAINT recipe_ingredients_quantity_check CHECK (quantity >= 0);

package edu.xjtlu.civa.core.data;

import edu.xjtlu.civa.api.AuthApi;
import edu.xjtlu.civa.api.HouseholdApi;
import edu.xjtlu.civa.api.InventoryApi;
import edu.xjtlu.civa.api.MediaApi;
import edu.xjtlu.civa.api.PlanningApi;
import edu.xjtlu.civa.api.ProfileApi;
import edu.xjtlu.civa.api.RecipeApi;
import edu.xjtlu.civa.api.RecordsApi;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Timeout;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.converter.gson.GsonConverterFactory;

/** Real transport; keep one instance per application session container. */
public final class RealApiProvider implements ApiProvider {
    private final Retrofit retrofit;
    private final SessionStore sessions;

    public RealApiProvider(String baseUrl, SessionStore sessions) {
        Objects.requireNonNull(sessions, "sessions");
        this.sessions = sessions;
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    SessionStore.Snapshot session = request.tag(SessionStore.Snapshot.class);
                    String path = request.url().encodedPath();
                    if (session != null && session.token != null
                            && !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register")) {
                        request = request.newBuilder().header("Authorization", "Bearer " + session.token).build();
                    }
                    okhttp3.Response response = chain.proceed(request);
                    RawResponseValidation validation = request.tag(RawResponseValidation.class);
                    if (response.isSuccessful() && validation != null && validation.enabled()) {
                        try {
                            if (response.body() == null) throw new RawResponseValidation.InvalidResponseException();
                            var source = response.body().source().peek();
                            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                            try (var reader = new InputStreamReader(source.inputStream(), decoder)) {
                                validation.validate(reader);
                            }
                        } catch (IOException | RuntimeException ex) {
                            response.close();
                            throw ex;
                        }
                    }
                    return response;
                }).build();
        retrofit = new Retrofit.Builder().baseUrl(baseUrl)
                .callFactory(request -> client.newCall(request.newBuilder()
                        .tag(SessionStore.Snapshot.class, sessions.snapshot())
                        .tag(RawResponseValidation.class, new RawResponseValidation()).build()))
                .addCallAdapterFactory(new CallAdapter.Factory() {
                    @Override public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
                        if (getRawType(returnType) != Call.class) return null;
                        if (!(returnType instanceof ParameterizedType)) throw new IllegalArgumentException("Call must have a response type");
                        Type responseType = getParameterUpperBound(0, (ParameterizedType) returnType);
                        return new CallAdapter<Object, Call<Object>>() {
                            @Override public Type responseType() { return responseType; }
                            @Override public Call<Object> adapt(Call<Object> call) {
                                synchronized (sessions) {
                                    return new BoundCall<>(call, sessions, sessions.snapshot().generation);
                                }
                            }
                        };
                    }
                })
                .addConverterFactory(GsonConverterFactory.create(ApiCalls.GSON)).build();
    }

    @Override public HouseholdApi household() { return retrofit.create(HouseholdApi.class); }
    @Override public ProfileApi profile() { return retrofit.create(ProfileApi.class); }
    @Override public RecipeApi recipe() { return retrofit.create(RecipeApi.class); }
    @Override public InventoryApi inventory() { return retrofit.create(InventoryApi.class); }
    @Override public PlanningApi planning() { return retrofit.create(PlanningApi.class); }
    @Override public RecordsApi records() { return retrofit.create(RecordsApi.class); }
    AuthApi auth() { return retrofit.create(AuthApi.class); }
    MediaApi media() { return retrofit.create(MediaApi.class); }
    boolean uses(SessionStore store) { return sessions == store; }

    private static final class BoundCall<T> implements Call<T> {
        private final Call<T> delegate;
        private final SessionStore sessions;
        private final long generation;

        BoundCall(Call<T> delegate, SessionStore sessions, long generation) {
            this.delegate = delegate;
            this.sessions = sessions;
            this.generation = generation;
            delegate.request();
        }

        @Override public Response<T> execute() throws IOException { return delegate.execute(); }
        @Override public void enqueue(Callback<T> callback) {
            delegate.enqueue(new Callback<T>() {
                @Override public void onResponse(Call<T> ignored, Response<T> response) { callback.onResponse(BoundCall.this, response); }
                @Override public void onFailure(Call<T> ignored, Throwable error) { callback.onFailure(BoundCall.this, error); }
            });
        }
        @Override public boolean isExecuted() { return delegate.isExecuted(); }
        @Override public void cancel() { delegate.cancel(); }
        @Override public boolean isCanceled() { return delegate.isCanceled(); }
        @Override public Request request() { return delegate.request(); }
        @Override public Timeout timeout() { return delegate.timeout(); }
        @Override public Call<T> clone() {
            synchronized (sessions) {
                if (sessions.snapshot().generation != generation) throw new IllegalStateException("Session changed; create a new request");
                return new BoundCall<>(delegate.clone(), sessions, generation);
            }
        }
    }
}

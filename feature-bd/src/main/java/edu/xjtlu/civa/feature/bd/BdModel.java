package edu.xjtlu.civa.feature.bd;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import edu.xjtlu.civa.core.data.*;
import edu.xjtlu.civa.core.ui.ApiError;
import java.util.*;
import retrofit2.Call;

/** Session-scoped requests and durable, explicit idempotent writes shared inside BD only. */
public abstract class BdModel extends ViewModel {
  protected final ApiProvider api;
  protected final ApiCalls calls;
  protected final SessionStore sessions;
  private final List<Call<?>> active = new ArrayList<>();
  public final MutableLiveData<Integer> changed = new MutableLiveData<>(0);
  public String message = "";
  public boolean busy;
  private int revision;
  private boolean disposed;

  protected BdModel(ApiProvider api, ApiCalls calls, SessionStore sessions) {
    this.api = api;
    this.calls = calls;
    this.sessions = sessions;
  }

  public void emit() {
    changed.setValue(++revision);
  }

  protected <T> void read(Call<T> call, java.util.function.Consumer<T> success) {
    active.add(call);
    calls.enqueue(
        call,
        new ApiCallback<T>() {
          @Override
          public void onSuccess(T value) {
            active.remove(call);
            if (disposed) return;
            success.accept(value);
            emit();
          }

          @Override
          public void onError(ApiError error) {
            active.remove(call);
            if (!disposed) fail(error);
          }
        });
  }

  protected void fail(ApiError error) {
    busy = false;
    message =
        error.code.equals("CONTRACT_NOT_IMPLEMENTED")
            ? "依赖接口尚未实现，请与 AC/EF 联调（未保存）"
            : error.code.equals("VERSION_CONFLICT")
                ? "内容已被其他成员修改，请刷新核对后再操作"
                : error.code.equals("PLAN_LOCKED")
                    ? "餐次已锁定，请先解锁"
                    : error.code.equals("NETWORK_ERROR") || error.code.equals("TIMEOUT")
                        ? "网络不可用或结果待确认，请勿重复新建操作"
                        : error.message;
    if (error.traceId != null) message += "\n追踪编号：" + error.traceId;
    emit();
  }

  protected <T> void write(
      String operation,
      String key,
      String payload,
      Call<T> call,
      java.util.function.Consumer<T> success) {
    busy = true;
    emit();
    String actor = sessions.currentUserId();
    calls
        .pendingWrites()
        .record(
            key,
            actor,
            operation.equals("createMealEntry") ? null : sessions.currentHouseholdId(),
            operation,
            payload,
            null,
            null,
            new ApiCallback<Void>() {
              @Override
              public void onSuccess(Void ignored) {
                if (!disposed && java.util.Objects.equals(actor, sessions.currentUserId()))
                  submit(operation, key, payload, call, success, actor);
              }

              @Override
              public void onError(ApiError error) {
                fail(error);
              }
            });
  }

  protected <T> void retry(
      String operation,
      String key,
      String payload,
      Call<T> call,
      java.util.function.Consumer<T> success) {
    busy = true;
    emit();
    String actor = sessions.currentUserId();
    calls
        .pendingWrites()
        .listPending(
            actor,
            new ApiCallback<List<PendingWrite>>() {
              @Override
              public void onSuccess(List<PendingWrite> values) {
                if (disposed || !Objects.equals(actor, sessions.currentUserId())) return;
                for (PendingWrite value : values)
                  if (value.key.equals(key)) {
                    if (value.needsReview
                        || !value.operationId.equals(operation)
                        || !value.canonicalPayload.equals(payload)) {
                      busy = false;
                      message = "待确认请求需要人工核对，未发送";
                      emit();
                      return;
                    }
                    submit(operation, key, payload, call, success, actor);
                    return;
                  }
                write(operation, key, payload, call, success);
              }

              @Override
              public void onError(ApiError error) {
                if (!disposed) fail(error);
              }
            });
  }

  private <T> void submit(
      String operation,
      String key,
      String payload,
      Call<T> call,
      java.util.function.Consumer<T> success,
      String actor) {
    active.add(call);
    calls.enqueue(
        call,
        new ApiCallback<T>() {
          @Override
          public void onSuccess(T value) {
            active.remove(call);
            calls
                .pendingWrites()
                .confirm(
                    actor,
                    key,
                    new ApiCallback<Void>() {
                      @Override
                      public void onSuccess(Void ignored) {
                        if (disposed || !java.util.Objects.equals(actor, sessions.currentUserId()))
                          return;
                        busy = false;
                        message = "已保存";
                        success.accept(value);
                        emit();
                      }

                      @Override
                      public void onError(ApiError error) {
                        if (disposed || !Objects.equals(actor, sessions.currentUserId())) return;
                        busy = false;
                        message = "服务端已保存，本地待确认标记清理失败，请核对后处理";
                        success.accept(value);
                        emit();
                      }
                    });
          }

          @Override
          public void onError(ApiError error) {
            active.remove(call);
            fail(error);
          }
        });
  }

  @Override
  protected void onCleared() {
    disposed = true;
    for (Call<?> call : new ArrayList<>(active)) call.cancel();
    active.clear();
  }

  public static final class Intent {
    public final String key, canonicalPayload;

    public Intent(String key, String payload) {
      this.key = key;
      this.canonicalPayload = payload;
    }
  }
}

package edu.xjtlu.civa.feature.bd;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import edu.xjtlu.civa.core.data.ApiCallback;
import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.IdempotentWriter;
import edu.xjtlu.civa.core.data.PendingWrite;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.core.ui.ApiError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import retrofit2.Call;

/**
 * BD view-model base: session-scoped reads plus durable idempotent writes.
 *
 * <p>The write protocol itself lives in {@link IdempotentWriter}; this class only adds the screen
 * state (busy flag, message, change revision) and cancels outstanding calls when the screen dies.
 */
public abstract class BdModel extends ViewModel {
  protected final ApiProvider api;
  protected final ApiCalls calls;
  protected final SessionStore sessions;
  private final IdempotentWriter writer;
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
    this.writer = new IdempotentWriter(calls, sessions);
  }

  public void emit() {
    changed.setValue(++revision);
  }

  protected <T> void read(Call<T> call, Consumer<T> success) {
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
                    : error.code.equals(IdempotentWriter.PENDING_NEEDS_REVIEW)
                        ? "待确认请求需要人工核对，未发送"
                        : error.code.equals("NETWORK_ERROR") || error.code.equals("TIMEOUT")
                            ? "网络不可用或结果待确认，请勿重复新建操作"
                            : error.message;
    if (error.traceId != null) message += "\n追踪编号：" + error.traceId;
    emit();
  }

  protected <T> void write(
      String operation, String key, String payload, Call<T> call, Consumer<T> success) {
    busy = true;
    emit();
    active.add(call);
    writer.write(
        operation,
        key,
        payload,
        householdScope(operation),
        call,
        result(call, success));
  }

  protected <T> void retry(
      String operation, String key, String payload, Call<T> call, Consumer<T> success) {
    busy = true;
    emit();
    active.add(call);
    writer.retry(
        operation,
        key,
        payload,
        householdScope(operation),
        call,
        result(call, success));
  }

  /** Personal entries carry no household scope; everything else belongs to the current household. */
  @Nullable
  private String householdScope(String operation) {
    return operation.equals("createMealEntry") ? null : sessions.currentHouseholdId();
  }

  private <T> IdempotentWriter.Result<T> result(Call<T> call, Consumer<T> success) {
    return new IdempotentWriter.Result<T>() {
      @Override
      public void onSuccess(T value) {
        active.remove(call);
        if (disposed) return;
        busy = false;
        message = "已保存";
        success.accept(value);
        emit();
      }

      @Override
      public void onError(ApiError error) {
        active.remove(call);
        if (!disposed) fail(error);
      }
    };
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

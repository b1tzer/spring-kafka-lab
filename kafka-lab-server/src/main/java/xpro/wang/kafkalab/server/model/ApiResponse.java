package xpro.wang.kafkalab.server.model;

/**
 * Standard API response wrapper.
 *
 * @param <T> payload type
 */
public record ApiResponse<T>(boolean success, String message, T data) {

  /**
   * Creates a successful response.
   *
   * @param message response message
   * @param data response payload
   * @param <T> payload type
   * @return successful response object
   */
  public static <T> ApiResponse<T> ok(String message, T data) {
    return new ApiResponse<>(true, message, data);
  }

  /**
   * Creates a failed response without payload.
   *
   * @param message error message
   * @param <T> payload type
   * @return failed response object
   */
  public static <T> ApiResponse<T> fail(String message) {
    return new ApiResponse<>(false, message, null);
  }
}

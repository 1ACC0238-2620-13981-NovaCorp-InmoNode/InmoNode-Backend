package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.function.Function;
import java.util.function.ToLongFunction;

@NullMarked
public final class ResponseEntityAssembler {

    /** The header that carries the unpaginated count of a listing; CORS exposes it to the web client. */
    public static final String TOTAL_COUNT = "Total-Count";

    private ResponseEntityAssembler(){}

    /**
     * Converts a Result into an HTTP response using the provided success resource assembler.
     * Failure responses are delegated to ErrorResponseAssembler.
     *
     * @param result the application result
     * @param successResourceAssembler function that maps success value to response resource
     * @param successStatus HTTP status to use for successful responses
     * @param <T> success value type
     * @param <R> success response resource type
     * @return response entity for success or failure
     */
    public static <T, R> ResponseEntity<?> toResponseEntityFromResult(
            Result<T, ApplicationError> result,
            Function<T, R> successResourceAssembler,
            HttpStatusCode successStatus
    ){
        return switch (result) {
            case Result.Success<T, ApplicationError> success ->
                new ResponseEntity<>(successResourceAssembler.apply(success.value()), successStatus);
            case Result.Failure<T, ApplicationError> failure ->
                    ErrorResponseAssembler.toErrorResponseFromApplicationError(failure.error());
        };
    }

    /**
     * Converts a Result holding one page of a listing (US-46) into an HTTP response: see
     * {@link #toPageResponseEntity(Object, long)}. Failure responses are delegated to ErrorResponseAssembler.
     *
     * @param totalCount function that reads the unpaginated count from the success value
     */
    public static <T, R> ResponseEntity<?> toPageResponseEntityFromResult(
            Result<T, ApplicationError> result,
            Function<T, R> successResourceAssembler,
            ToLongFunction<T> totalCount
    ) {
        return switch (result) {
            case Result.Success<T, ApplicationError> success -> toPageResponseEntity(
                    successResourceAssembler.apply(success.value()), totalCount.applyAsLong(success.value()));
            case Result.Failure<T, ApplicationError> failure ->
                    ErrorResponseAssembler.toErrorResponseFromApplicationError(failure.error());
        };
    }

    /**
     * One page of a listing (US-46): 200 with the page, the count of every record in the {@code Total-Count} header
     * and {@code Cache-Control: no-store}, since a page goes stale as soon as a record is added.
     */
    public static <R> ResponseEntity<R> toPageResponseEntity(R page, long totalCount) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT, Long.toString(totalCount))
                .cacheControl(CacheControl.noStore())
                .body(page);
    }
}

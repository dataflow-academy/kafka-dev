package academy.dataflow.wind.streams;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A customer's order to move money from one account to another.
 *
 * @param transferId  unique id, e.g. {@code tx-20260917-143000-0042}
 * @param fromAccount the account that pays
 * @param toAccount   the account that receives
 * @param amountCents the amount in euro cents, always positive
 * @param timestamp   when the customer placed the order, epoch milliseconds
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BankTransfer(
        String transferId,
        String fromAccount,
        String toAccount,
        long amountCents,
        long timestamp) {
}

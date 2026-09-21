package academy.dataflow.wind.transactions;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * One side of a transfer on one account: a debit (money leaves the account)
 * or a credit (money arrives). A correctly booked transfer is exactly one
 * debit plus exactly one credit with the same transfer id.
 *
 * @param transferId     the transfer this booking belongs to
 * @param account        the account being booked
 * @param counterAccount the other side of the transfer
 * @param amountCents    the amount in euro cents, always positive
 * @param timestamp      when the transfer was ordered, epoch milliseconds
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Booking(
        String transferId,
        String account,
        String counterAccount,
        long amountCents,
        long timestamp) {

    /** The debit on the paying account. Key it by {@link #account()}. */
    public static Booking debit(BankTransfer transfer) {
        return new Booking(transfer.transferId(), transfer.fromAccount(), transfer.toAccount(),
                transfer.amountCents(), transfer.timestamp());
    }

    /** The credit on the receiving account. Key it by {@link #account()}. */
    public static Booking credit(BankTransfer transfer) {
        return new Booking(transfer.transferId(), transfer.toAccount(), transfer.fromAccount(),
                transfer.amountCents(), transfer.timestamp());
    }
}

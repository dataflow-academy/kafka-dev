package example.common;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class BankTransferSupplier implements Supplier<BankTransfer> {
    List<String> RANDOM_NAMES = List.of("Alice", "Bob", "Charlie", "Dave", "Eve", "Francis");
    private final Random rand = new Random();

    private final int msgsPerSec;
    private int msgCount;

    public BankTransferSupplier(int msgsPerSec) {
        this.msgsPerSec = msgsPerSec;
        msgCount = 0;
    }

    @Override
    public BankTransfer get() {
        BankTransfer transfer = new BankTransfer();
        transfer.sender_account = RANDOM_NAMES.get(rand.nextInt(RANDOM_NAMES.size()));
        transfer.receiver_account = RANDOM_NAMES.get(rand.nextInt(RANDOM_NAMES.size()));
        transfer.amount = rand.nextInt(100) * (rand.nextInt(10) + 1);
        transfer.isSuspicious = rand.nextDouble() < 0.30;
        if (msgCount >= msgsPerSec && msgsPerSec != -1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            msgCount = 0;
        }
        msgCount++;
        return transfer;
    }
}

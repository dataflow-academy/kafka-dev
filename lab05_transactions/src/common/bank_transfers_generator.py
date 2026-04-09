import random
import time


class BankTransferGenerator:
    msgs_per_sec = 0
    names = ["Alice", "Bob", "Charlie", "Dave", "Eve", "Francis"]
    msg_count = 0

    def __init__(self, msgs_per_sec):
        self.msgs_per_sec = msgs_per_sec

    def __iter__(self):
        return self

    def __next__(self):
        return self.next()

    def next(self):
        transfer = {
            'sender_account': random.choice(self.names),
            'receiver_account': random.choice(self.names),
            'amount': random.randint(0, 100) * (random.randint(0, 10) + 1),
            'suspicious': random.uniform(0, 1) < 0.30
        }

        if self.msg_count >= self.msgs_per_sec and self.msgs_per_sec != -1:
            time.sleep(1)
            self.msg_count = 0
        self.msg_count += 1
        return transfer

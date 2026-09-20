# Bank: what a crash between the two writes does

Your guess before measuring: after a crash between the two bookings and a
restart, how many bookings are too many? ______

Every group measures one order. The table fills up once all groups report.
Compare increments only, not the totals in the topics.

## At-least-once, with the automatic commit

The consumer commits the offsets on its own, every `auto.commit.interval.ms`.
Both rows crash at transfer 500 and restart with `HALT_AT_TRANSFER = 0`.

| First booking | Transfers +? | Debits +? | Credits +? | Duplicate debits | Duplicate credits | Who benefits |
|---|---|---|---|---|---|---|
| debit | | | | | | |
| credit | | | | | | |

Right after the crash, before the restart: debits +______, credits +______

Which of the two is one short, and why that one?

From the crash until the app books again: ______ seconds. Why that long?

## At-most-once, with your own commit (going deeper)

`enable.auto.commit=false` and `commitSync()` before the booking.

| First booking | Debits +? | Credits +? | Bookings missing | Duplicates |
|---|---|---|---|---|
| | | | | |

Which transfer is the missing booking for? ______

How would you find that transfer without knowing where the app crashed?

## Conclusion

Is there a safe order?

What is missing to book every transfer exactly-once?

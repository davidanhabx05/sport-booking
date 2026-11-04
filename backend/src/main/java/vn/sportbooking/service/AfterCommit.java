package vn.sportbooking.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chạy một việc sau khi transaction hiện tại commit thành công (gửi email, đẩy thông báo).
 * Nếu transaction rollback thì việc đó bị bỏ qua → không gửi "đặt sân thành công" cho đơn không tồn tại.
 */
public final class AfterCommit {
    private AfterCommit() {}

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}

package com.paisaflow.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class BackupCryptoTest {
    @Test public void encryptedBackupRoundTrips() throws Exception {
        char[] password = "correct horse battery".toCharArray();
        byte[] encrypted = BackupCrypto.encrypt("{\"format\":\"paisaflow-backup\"}", password);
        assertTrue(BackupCrypto.isEncrypted(encrypted));
        assertEquals("{\"format\":\"paisaflow-backup\"}", BackupCrypto.decrypt(encrypted, password));
    }

    @Test public void tamperingIsRejected() throws Exception {
        char[] password = "correct horse battery".toCharArray();
        byte[] encrypted = BackupCrypto.encrypt("private", password);
        encrypted[encrypted.length - 1] ^= 1;
        try {
            BackupCrypto.decrypt(encrypted, password);
            fail("Tampered backup must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("damaged"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortPasswordIsRejected() throws Exception {
        BackupCrypto.encrypt("private", "short".toCharArray());
    }
}

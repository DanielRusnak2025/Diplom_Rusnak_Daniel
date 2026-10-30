package org.example.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public final class PasswordHasher {

    /** RFC 9106 second recommended parameters. */
    private static final int ITERATIONS  = 3;
    private static final int MEMORY_KIB  = 65_536; // 64 MiB
    private static final int PARALLELISM = 4;

    /** DoS-защита: Argon2id дорогой, длинный пароль — дороже. */
    public static final int MAX_PASSWORD_LENGTH = 128;

    private static final Argon2 ARGON2 =
            Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private PasswordHasher() {}

    /** Returns PHC string: $argon2id$v=19$m=65536,t=3,p=4$<salt>$<hash>. */
    public static String hash(String password) {
        char[] chars = password.toCharArray();
        try {
            return ARGON2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, chars);
        } finally {
            ARGON2.wipeArray(chars);
        }
    }

    /** Constant-time comparison; returns false on any mismatch or error. */
    public static boolean verify(String storedHash, String password) {
        char[] chars = password.toCharArray();
        try {
            return ARGON2.verify(storedHash, chars);
        } finally {
            ARGON2.wipeArray(chars);
        }
    }
}

package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.tokens.opaque.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecureRandomRefreshTokenGeneratorTest {

    private final SecureRandomRefreshTokenGenerator generator = new SecureRandomRefreshTokenGenerator();

    @Test
    void generatesDistinctUrlSafeTokensOf256Bits() {
        var first = generator.generate();
        var second = generator.generate();

        assertNotEquals(first, second);
        assertEquals(43, first.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void hashIsADeterministicSha256InHexThatFitsTheColumn() {
        var token = generator.generate();

        var hash = generator.hash(token);

        assertEquals(hash, generator.hash(token));
        assertNotEquals(hash, generator.hash(generator.generate()));
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", generator.hash("abc"));
    }
}

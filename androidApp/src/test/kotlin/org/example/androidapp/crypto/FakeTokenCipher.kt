package org.example.androidapp.crypto

class FakeTokenCipher : TokenCipher {
    override fun encrypt(plaintext: String) = "enc:$plaintext"
    override fun decrypt(ciphertext: String): String {
        require(ciphertext.startsWith("enc:")) { "Not encrypted: $ciphertext" }
        return ciphertext.removePrefix("enc:")
    }
}

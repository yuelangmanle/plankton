import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class DocEncryptor {
    private static final String DOC_MAGIC = "DOCENC1";
    private static final int SALT_SIZE = 16;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 16;
    private static final int PBKDF2_ITERATIONS = 120000;
    private static final int KEY_BITS = 256;

    private DocEncryptor() {}

    private static byte[] deriveKey(String password, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private static byte[] encrypt(String password, byte[] plain) throws Exception {
        var salt = new byte[SALT_SIZE];
        var iv = new byte[IV_SIZE];
        var rng = new SecureRandom();
        rng.nextBytes(salt);
        rng.nextBytes(iv);

        var key = deriveKey(password, salt);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        var spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
        var encrypted = cipher.doFinal(plain);

        var cipherText = Arrays.copyOfRange(encrypted, 0, encrypted.length - TAG_SIZE);
        var tag = Arrays.copyOfRange(encrypted, encrypted.length - TAG_SIZE, encrypted.length);
        var magic = DOC_MAGIC.getBytes(StandardCharsets.US_ASCII);

        var output = new byte[magic.length + salt.length + iv.length + tag.length + cipherText.length];
        var offset = 0;
        System.arraycopy(magic, 0, output, offset, magic.length);
        offset += magic.length;
        System.arraycopy(salt, 0, output, offset, salt.length);
        offset += salt.length;
        System.arraycopy(iv, 0, output, offset, iv.length);
        offset += iv.length;
        System.arraycopy(tag, 0, output, offset, tag.length);
        offset += tag.length;
        System.arraycopy(cipherText, 0, output, offset, cipherText.length);
        return output;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: DocEncryptor <password> <inputPath> <outputPath>");
            System.exit(1);
        }
        var password = args[0];
        var input = Path.of(args[1]);
        var output = Path.of(args[2]);
        var plain = Files.readAllBytes(input);
        var encrypted = encrypt(password, plain);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, encrypted);
        System.out.println("Wrote " + output);
    }
}

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCrypt;

/** ローカル確認アカウントのパスワードを製品と同じ BCrypt でハッシュ化する。 */
final class BootstrapBePassword {

    /** 標準入力のパスワード一行を読み、ハッシュだけを標準出力に返す。 */
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var password = input.readLine();
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Missing review password");
        }
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(12)));
    }
}

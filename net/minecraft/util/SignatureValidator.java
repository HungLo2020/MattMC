package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Signature validator for cryptographic verification.
 * Simplified for offline mode - no Mojang services key validation.
 */
public interface SignatureValidator {
	SignatureValidator NO_VALIDATION = (signatureUpdater, bs) -> true;
	Logger LOGGER = LogUtils.getLogger();

	boolean validate(SignatureUpdater signatureUpdater, byte[] bs);

	default boolean validate(byte[] bs, byte[] cs) {
		return this.validate(output -> output.update(bs), cs);
	}

	private static boolean verifySignature(SignatureUpdater signatureUpdater, byte[] bs, Signature signature) throws SignatureException {
		signatureUpdater.update(signature::update);
		return signature.verify(bs);
	}

	static SignatureValidator from(PublicKey publicKey, String string) {
		return (signatureUpdater, bs) -> {
			try {
				Signature signature = Signature.getInstance(string);
				signature.initVerify(publicKey);
				return verifySignature(signatureUpdater, bs, signature);
			} catch (Exception var5) {
				LOGGER.error("Failed to verify signature", (Throwable)var5);
				return false;
			}
		};
	}
}

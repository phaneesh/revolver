package io.dropwizard.revolver.util;

import org.apache.commons.codec.digest.DigestUtils;

public class CommonUtils {

    private CommonUtils(){

    }

    public static String computeHash(String config) {
        return DigestUtils.sha512Hex(config);
    }
}

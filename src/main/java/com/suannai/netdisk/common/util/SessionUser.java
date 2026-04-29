package com.suannai.netdisk.common.util;

import java.io.Serializable;

public record SessionUser(Long id, String username) implements Serializable {
}

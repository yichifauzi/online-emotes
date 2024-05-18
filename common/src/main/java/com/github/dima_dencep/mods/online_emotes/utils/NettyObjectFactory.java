/*
 * Copyright 2023 - 2024 dima_dencep.
 *
 * Licensed under the Open Software License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *     https://spdx.org/licenses/OSL-3.0.txt
 */

package com.github.dima_dencep.mods.online_emotes.utils;

import com.github.dima_dencep.mods.online_emotes.ConfigExpectPlatform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyObjectFactory {
    private static final AtomicInteger counter = new AtomicInteger();
    private static final ThreadFactory threadFactory = (runnable) -> {
        Thread thread = new Thread(runnable);
        thread.setName(String.format("OnlineEmotes Thread #%d", counter.incrementAndGet()));
        thread.setDaemon(true);
        return thread;
    };

    public static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(ConfigExpectPlatform.threads(), threadFactory);
}

/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;

// Jade support (only loaded when Jade is installed). Jade's own energy bar reads the controller's FE capability, which
// reports the whole structure's buffer; this adds the network's status, lanes and structure. The Power Inlet shows what
// it's receiving and the Segment Isolator whether it's separating two segments (InfrastructureProviders).
@WailaPlugin
public class EncodedLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NetworkControllerProvider.INSTANCE, NetworkControllerBlockEntity.class);
        registration.registerBlockDataProvider(InfrastructureProviders.PowerInlet.INSTANCE, PowerInletBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(NetworkControllerProvider.Client.INSTANCE, NetworkControllerBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.PowerInlet.Client.INSTANCE, PowerInletBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.SegmentIsolator.INSTANCE, SegmentIsolatorBlock.class);
    }
}

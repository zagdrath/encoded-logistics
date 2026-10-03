/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.LithographyPressBlock;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// Jade support (only loaded when Jade is installed). Jade's own energy bar reads the controller's FE capability, which
// reports the whole structure's buffer; this adds the network's status, lanes and structure. The Power Inlet shows what
// it's receiving and the Segment Isolator whether it's separating two segments (InfrastructureProviders). Cables and part
// hosts are named after their pick-block item (the part or facade looked at), which Jade only does for blocks marked
// to pick; otherwise it would use the block's own name.
@WailaPlugin
public class EncodedLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NetworkControllerProvider.INSTANCE, NetworkControllerBlockEntity.class);
        registration.registerBlockDataProvider(InfrastructureProviders.PowerInlet.INSTANCE, PowerInletBlockEntity.class);
        registration.registerBlockDataProvider(InfrastructureProviders.LithographyPress.INSTANCE, LithographyPressBlockEntity.class);
        ModBlocks.allCables().forEach(cable -> registration.blockOperations().pick(cable.getKey()));
        registration.blockOperations().pick(ModBlocks.PART_HOST.getKey());
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(NetworkControllerProvider.Client.INSTANCE, NetworkControllerBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.PowerInlet.Client.INSTANCE, PowerInletBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.SegmentIsolator.INSTANCE, SegmentIsolatorBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.LithographyPress.Client.INSTANCE, LithographyPressBlock.class);
        registration.registerBlockComponent(InfrastructureProviders.DriveBay.INSTANCE, DriveBayBlock.class);
    }
}

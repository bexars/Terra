package io.left.rightmesh.libdtn.core.spi.block;

import io.left.rightmesh.libdtn.common.utils.Log;
import io.left.rightmesh.libdtn.core.api.ExtensionManagerApi;

/**
 * Contract to be fulfilled by a module that introduces new ExtensionBlock.
 *
 * @author Lucien Loiseau on 03/11/18.
 */
public interface BlockSpi {

    void init(ExtensionManagerApi extensionManager, Log logger);

}

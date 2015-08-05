package com.issmMod.Main;

import com.issmMod.lib.ReferenceStrings;
import com.issmMod.Main.*;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.block.material.*;
import net.minecraft.block.*;


//Sets up the mod parameters declared in ReferenceStrings.java
@Mod(modid = ReferenceStrings.MODID, name = ReferenceStrings.NAME, 
		version = ReferenceStrings.VERSION)

/*
 * TODO: Make entities drown based on water.
 * TODO: Add tools to make player feel like god.
 */
/**
 * This is the main class for the mod, where its aspects are registered with the game
 * through Forge's event system.
 * @author Jed Gentry
 * @since 3/20/2015
 *
 */
public class ISSM_Mod {
		
		//Start classes for registration.
		//PreInit for the game, this is where everything before world load should be processed.
		/**
		 * PreInit for the game, this is where everything before the world is loaded should be processed.
		 * @param event - Event is fired when the PreInitialization occurs.
		 */
		public void preInit(FMLPreInitializationEvent event)
		{
		}
		/**
		 * This is when the main initialization game loop is hit.
		 * @param event - Fired when the game goes through its default Initialization loop.
		 */
		public void init(FMLInitializationEvent event)
		{
		}
		/**
		 * This is fired after the game is done initializing, in case any part is dependent on data generated
		 * from the Initialization loop.
		 * @param PostEvent - Event is fired after game Initialization is complete.
		 */
		@EventHandler
		public static void postInit(FMLPostInitializationEvent PostEvent)
		{
			//This needs to be here due to new CHUNK's now being properly culled! :)
			MinecraftForge.EVENT_BUS.register(new BlockHandler());
		}
}


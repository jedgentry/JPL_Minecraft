package com.issmMod.Main;

import java.io.*;
import java.util.concurrent.*;

import javax.sound.midi.Soundbank;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldEvent;
import net.minecraftforge.client.event.RenderWorldEvent.Pre;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.client.event.sound.SoundResultEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * TODO: GET RID OF THE IF CHECK ON LOWERING THE WATER, Flood height already knows the correct position so the check is unnecessary anyways.
 * @author Jed Gentry
 * @since 3/20/2015
 * RESOURCE: https://dl.dropboxusercontent.com/s/h777x7ugherqs0w/forgeevents.html
 */
public class BlockHandler {
	//Number of ice blocks destroyed.
	private double iceDestroyed = 0;
	//Number of ice blocks placed.
	private double icePlaced = 0;
	//The starting sea level in the player's world.
	private int initialSeaLevel = 0;
	//The amount of blocks needed to increment or decrement a flood.
	private double floodTick = 2.0;
	//The current height that the flooded water from the mod is at.
	private int floodHeight = 0;
	//The number of meta values in a water block.
	private static int floodMeta = 7;
	//This is the smallest water block that is able to be placed. in the vanilla game.
	private static final int WATER_MIN_META = 7;
	//This is the largest water block that is able to be placed within the game world.
	private static final int WATER_MAX_META = 0;
	//Amount of blocks in the X axis of a world chunk.
	private static final int CHUNK_X_INCREMENT = 16;
	//Amount of blocks in the Z axis of a world chunk.
	private static final int CHUNK_Z_INCREMENT = 16;
	//Amount of blocks in the Y axis of a world chunk.
	private static final int CHUNK_Y_INCREMENT = 256;
	//The amount of chunks loaded into the client. ** DEPRECATED **
	private static int CHUNK_RANGE = 0;
	//The amount of chunks in rendering distance to the client.
	private static int RENDER_RANGE = 0;
	//The amount of chunks we will update. 1 = 100%, 0 = 0% ** THIS SHOULDNT BE NEEDED, IS NOW RENDER BASED **
	private static double PERFORMANCE_MODIFIER = 1;
	//Amount of processor cores to have our block change threads on. O(16 * 16 * CHUNK_RANGE)
	private static Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors(), true);
	/*
	 * This is the amount of time it takes for a player to travel one chunk while sprinting, this is rounded slightly down (by 0.001),
	 * so that there is no case where the chunks around the player will not be updated.
	 */
	private static long UPDATE_SLEEP = 2850;
	/*
	 * TODO: Make flood level resume previous events.
	 * This can be done by saving flood level and reading it back in.
	 */
	/**
	 * Gets the render distance after post init event.
	 */
	public void getRenderDistance()
	{
		//Gets the amount of chunks that the client will render.
		RENDER_RANGE = Minecraft.getMinecraft().getRenderManager().options.renderDistanceChunks + 1;
	}
	/**
	 * Function is triggered when a block is broken in the game world.
	 * @param event the object containing the details of the event. 
	 */
	@SubscribeEvent
	public void onBlockBreakEvent(BlockEvent.BreakEvent event)
	{
		//Gets the initial sea level.
		try 
		{
			setupSeaLevel();
		}
		catch (NumberFormatException e) 
		{
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		if(event.state.getBlock() == Blocks.ice || event.state.getBlock() == Blocks.packed_ice ||
				event.state.getBlock() == Blocks.snow || event.state.getBlock() == Blocks.snow_layer)
		{
			iceDestroyed++;
			if(iceDestroyed % floodTick == 0 && floodHeight < 254 && floodHeight > 0)
			{
				incrementSeaLevel(); //Increment if we have hit our flood threshold.
				if(floodHeight == event.pos.getY() || floodHeight == event.pos.getY() - 1)
					Minecraft.getMinecraft().theWorld.getChunkFromBlockCoords(event.pos).setBlockState(event.pos, BlockDynamicLiquid.getFlowingBlock(Material.water).getStateFromMeta(floodMeta));
			}
		}
	}
	
	/**
	 * Function will increment the sea level by one if called.
	 */
	public void incrementSeaLevel()
	{
		if(floodMeta != WATER_MAX_META) 
			floodMeta--;
		else 
		{
			floodMeta = WATER_MIN_META;
			floodHeight += 1;
		}
		threadedFlood(); //Trigger our flood instantly.
		iceDestroyed = 1;
	}
	
	/**
	 * Multi-threaded flooding algorithm. This problem is inherently computationally expensive,
	 * the game engine was not built to do this, and as such the data is not structured for
	 * this task.
	 */
	public void threadedFlood()
	{				
		try 
		{
		if(permits.tryAcquire(1, TimeUnit.SECONDS)) //Grab semaphore.
			try {
					Thread floodingThread = new Thread() 
					{
						public void run() 
						{
							triggerFlood(Minecraft.getMinecraft().theWorld);
						}
					};
					floodingThread.start();
			}
			finally
			{
				permits.release();
			}
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace(); //Print exception, however this should never happen.
		}	
	}
	
	/**
	 * Spawns the water blocks on that specific chunk (16 x 16 x 256) blocks.
	 * @param myChunk - The chunk on which to spawn the water blocks.
	 * @param x - The x position to potentially spawn a block.
	 * @param z - The Z position to potentially spawn a block.
	 */
	public void spawnWaterOnChunk(Chunk myChunk, int x, int z)
	{
		if(myChunk.getBlock(x, floodHeight, z) == Blocks.air)
		{
			BlockPos pos = new BlockPos((myChunk.xPosition * CHUNK_X_INCREMENT) + x, floodHeight, (myChunk.zPosition * CHUNK_Z_INCREMENT) + z);
			myChunk.setBlockState(pos, BlockDynamicLiquid.getFlowingBlock(Material.water).getStateFromMeta(floodMeta));
		}
	}
	
	/**
	 * Gets the current sea level of the world.
	 * @param world - the game world in which to judge the sea level.
	 */
	public void getSeaLevel(World world)
	{
		double currX = Minecraft.getMinecraft().getRenderViewEntity().posX; //Get camera position, this is the best indicator of a player's position in the world.
		double currZ = Minecraft.getMinecraft().getRenderViewEntity().posZ;
		for(double x = currX - (double)(RENDER_RANGE * PERFORMANCE_MODIFIER); x < currX + (double)(RENDER_RANGE * PERFORMANCE_MODIFIER); x += (double)CHUNK_X_INCREMENT)
		{
			for(double z = currZ - (double)(RENDER_RANGE * PERFORMANCE_MODIFIER); z < currZ + (double)(RENDER_RANGE * PERFORMANCE_MODIFIER); z += (double)CHUNK_Z_INCREMENT)
			{
				BlockPos pos = new BlockPos(currX, 0, currZ);
				Chunk myChunk = world.getChunkFromBlockCoords(pos);
				if(scanChunk(myChunk)) //Get teh sea level in the chunk.
					currZ += (double)CHUNK_Z_INCREMENT;
			}
		}
	}
	
	/**
	 * Scans a chunk to find the max water height (sea level).
	 * @param myChunk - The chunk that its currently scanning.
	 * @return boolean - true if found a water block, by an air or water,
	 * meaning max height.
	 */
	public boolean scanChunk(Chunk myChunk)
	{
		for(int x = 0; x < CHUNK_X_INCREMENT; x++)
		{
			for(int z = 0; z < CHUNK_Z_INCREMENT; z++)
			{
				for(int y = 0; y < CHUNK_Y_INCREMENT; y++)
				{
					if(myChunk.getBlock(x, y, z) == Blocks.water && myChunk.getBlock(x, y + 1, z) == Blocks.air || myChunk.getBlock(x, y + 1, z) == Blocks.ice)
					{
						initialSeaLevel = y;
						floodHeight = y + 1;
						if(myChunk.getBlock(x, y + 1, z) == Blocks.ice)
							floodHeight++;
						return true; //Break if the sea has been found, thankfully, water blocks spawn at the same max height in the world.
					}
				}
			}
		}
		return false;
	}
	/**
	 * Triggers a flood in the world.
	 * @param world - the game world to flood.
	 */
	public void triggerFlood(World world)
	{
		//TODO: Keep track of current flood level and increment chunks on CHUNKLOAD event.
		double currX = Minecraft.getMinecraft().getRenderViewEntity().posX;
		double currZ = Minecraft.getMinecraft().getRenderViewEntity().posZ;

		for(double x = currX - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x < currX + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x += CHUNK_X_INCREMENT)
		{
			for(double z = currZ - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z < currZ + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z += CHUNK_Z_INCREMENT)
			{
				BlockPos currPos = new BlockPos(x, 0, z);
				Chunk myChunk = world.getChunkFromBlockCoords(currPos);
				spawnWaterPerChunk(myChunk);
			}
		}
	}
	
	/**
	 * Spawns water on a per chunk basis.
	 * @param myChunk the chunk at which I'm spawning water.
	 */
	public void spawnWaterPerChunk(Chunk myChunk)
	{
		//Go through chunk position.
		for(int x = 0; x < CHUNK_X_INCREMENT; x++)
		{
			for(int z = 0; z < CHUNK_Z_INCREMENT; z++)
			{
				spawnWaterOnChunk(myChunk, x, z);
			}
		}
	}
	/**
	 * Catches up the chunks in a background operation.
	 * This is to handle new chunks that are loaded in around the player.
	 * @param myChunk - The chunk that the thread is updating.
	 */
	public void updateChunks(Chunk myChunk)
	{
		for(int x = 0; x < CHUNK_X_INCREMENT; x++)
		{
			for(int z = 0; z < CHUNK_Z_INCREMENT; z++)
			{
				if(floodHeight > initialSeaLevel && initialSeaLevel != 0)
					spawnWaterOnChunk(myChunk, x, z);
				else
					lowerWaterOnChunk(myChunk, x, z, floodHeight);
			}
		}
	}
	/**
	 * This scans the outlying chunks around the player and sleeps for average distance.
	 */
	public void threadedUpdate()
	{
			final Thread updateThread = new Thread()
			{
				public void run()
				{
					//Run until game ends on new thread.
					while(Minecraft.getMinecraft().isIntegratedServerRunning())
					{
					//Get the current position of the player.
						double currX = Minecraft.getMinecraft().getRenderViewEntity().posX;
						double currZ = Minecraft.getMinecraft().getRenderViewEntity().posZ;
						//Find the chunk and close it.
						for(double x = currX - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x < currX + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x += CHUNK_X_INCREMENT)
						{
							for(double z = currZ - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z < currZ + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z += CHUNK_Z_INCREMENT)
							{
								BlockPos currPos = new BlockPos(x, 0, z);
								Chunk myChunk = Minecraft.getMinecraft().theWorld.getChunkFromBlockCoords(currPos);
								updateChunks(myChunk);
							}
						}
						try
						{
							Thread.sleep(UPDATE_SLEEP);
						}
						catch (InterruptedException e) 
						{
							e.printStackTrace();
						}
					}
					//Write out update to the files.
					//Read the file data.
					PrintWriter writer = null;
					try 
					{
						writer = new PrintWriter("seaLevel", "UTF-8");
					}
					catch (FileNotFoundException e) 
					{
						e.printStackTrace();
					} 
					catch (UnsupportedEncodingException e) 
					{
						e.printStackTrace();
					}
					writer.println(floodHeight);
					writer.println(floodMeta);
					writer.close();
				}
			};
			updateThread.start();
	}
	/**
	 * Multi-threaded lower the sea level algorithm.
	 * @param seaLevelToLower - If two freeze events are triggered fast enough, this may be on a seperate level
	 * this handles that race condition.
	 */
	public void threadedLower(final int seaLevelToLower)
	{
		//Lower by one meta.
		try 
		{
			if(permits.tryAcquire(1, TimeUnit.SECONDS))
			try 
			{
				Thread loweringThread = new Thread() 
				{
					public void run() 
					{
						lowerSeaLevel(Minecraft.getMinecraft().theWorld, seaLevelToLower);
					}
				};
				loweringThread.start();
			}
			finally 
			{
				permits.release();
			}
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}
	/**
	 * Sets up the sea level for the chunk update functions to be 
	 * O(256 * 1 * 256 * NUM_CHUNKS)
	 * Without this function it would be
	 * O(256 * 256 * 256 * NUM_CHUNKS)
	 * @throws IOException - File not created, this shouldn't happen though.
	 * @throws NumberFormatException - Number not written correctly, this shouldnt happen though.
	 */
	public void setupSeaLevel() throws NumberFormatException, IOException
	{
		if(initialSeaLevel == 0)
		{
			getRenderDistance();
			getSeaLevel(Minecraft.getMinecraft().theWorld);
			File myFile = new File((Minecraft.getMinecraft().mcDataDir + "seaLevel").toString());
			if(myFile.exists())
			{
				/*
				 * File format is this
				 * INT - This is the actual sea height level.
				 * INT - Meta of the sea, this divides the blocks into eights.
				 */
				//Read the file data.
				FileReader cin = new FileReader(myFile);
				BufferedReader cinLine = new BufferedReader(cin);
				floodHeight = Integer.parseInt(cinLine.readLine());
				floodMeta = Integer.parseInt(cinLine.readLine());
				cinLine.close();
				cin.close();
				//Close file descriptors.
			}
			floodHeight--;
			threadedUpdate();
		}
	}
	/**
	 * Triggered when a block is placed in the game world.
	 * @param event the details of the block placed within the game world.
	 */
	@SubscribeEvent
	public void onBlockPlaceEvent(BlockEvent.PlaceEvent event)
	{
		try 
		{
			setupSeaLevel();
		} 
		catch (NumberFormatException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		if(event.placedBlock.getBlock() == Blocks.ice || event.placedBlock.getBlock() == Blocks.snow 
		|| event.placedBlock.getBlock() == Blocks.packed_ice || event.placedBlock.getBlock() == Blocks.snow_layer)
		{
			icePlaced++;
			if(icePlaced % floodTick == 0 && floodHeight > 0)
				decrementSeaLevel();
		}
	}
	
	/**
	 * Decrements the sea level by one seventh.
	 */
	public void decrementSeaLevel()
	{
		if(floodMeta != WATER_MIN_META) 
			floodMeta++;
		else
		{
			floodMeta = WATER_MAX_META;
			threadedLower(floodHeight);
			floodHeight--;
		}
		icePlaced = 1;
	}
	/**
	 * Lowers the sea level in the world.
	 * @param world the world in which to lower the sea level.
	 * @param seaLevelToLower - the sea level to lower.
	 */
	public void lowerSeaLevel(World world, int seaLevelToLower)
	{
		double currX = Minecraft.getMinecraft().getRenderViewEntity().posX;
		double currZ = Minecraft.getMinecraft().getRenderViewEntity().posZ;
		for(double x = currX - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x < currX + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_X_INCREMENT; x += CHUNK_X_INCREMENT)
		{
			for(double z = currZ - (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z < currZ + (RENDER_RANGE * PERFORMANCE_MODIFIER) * CHUNK_Z_INCREMENT; z += CHUNK_Z_INCREMENT)
			{
				BlockPos currPos = new BlockPos(x, 0, z);
				Chunk myChunk = world.getChunkFromBlockCoords(currPos);
				lowerWaterPerChunk(myChunk, seaLevelToLower);
			}
		}
	}
	
	/**
	 * Lowers the sea level on a per chunk basis.
	 * @param myChunk the chunk in which to lower the sea level.
	 * @param seaLevelToLower the sea level to lower.
	 */
	public void lowerWaterPerChunk(Chunk myChunk, int seaLevelToLower)
	{
		//Go through chunk position.
		for(int x = 0; x < CHUNK_X_INCREMENT; x++)
		{
			for(int z = 0; z < CHUNK_Z_INCREMENT; z++)
			{
				for(int y = seaLevelToLower; y < CHUNK_Y_INCREMENT; y++)
				{
					lowerWaterOnChunk(myChunk, x, z, y);
				}
			}
		}
	}
	/**
	 * Lowers the water on a per block basis per chunk.
	 * @param myChunk the chunk at which i'm lowering the sea level by block.
	 * @param x the x location of the block to potentially lower.
	 * @param z the z location of the block to potentially lower.
	 * @param seaLevelToLower the sea level that we're wiping from the map.
	 */
	public void lowerWaterOnChunk(Chunk myChunk, int x, int z, int seaLevelToLower)
	{
		BlockPos pos = new BlockPos((myChunk.xPosition * CHUNK_X_INCREMENT) + x, seaLevelToLower, (myChunk.zPosition * CHUNK_Z_INCREMENT) + z);
		if(floodMeta != WATER_MIN_META && myChunk.getBlock(pos) == Blocks.water || myChunk.getBlock(pos) == Blocks.flowing_water)
			myChunk.setBlockState(pos, BlockDynamicLiquid.getFlowingBlock(Material.water).getStateFromMeta(floodMeta));
		if(floodMeta == WATER_MIN_META && myChunk.getBlock(pos) == Blocks.water || myChunk.getBlock(pos) == Blocks.flowing_water || myChunk.getBlock(pos) == Blocks.air)
			myChunk.setBlockState(pos, Blocks.air.getDefaultState());
	}
	
}
# JPL_Minecraft

This is a protoype I did for Univeristy of California, Irvine research under the supervision of employees Jet Propulsion Laboratory who were at the time participating in the ISSM Program (Ice Sheet System Model). At the time we were exploring how to educate a younger audience to the dangers of global warming and this was one of the resulting projects. The mod simulates flooding based on the destruction and placement of various ice blocks or snow blocks in the game world, resulting in flooding or the opposite if too many were place. The code ended up being heavily threaded in order to avoid conflicts with the game's internal server update rate. At any point the player can turn off the mod and no damage is done to the player's world.

# Threading Model
Editing the world in 3D space is at its core a O(N^3) problem, however on the first event, the actual sea height of the world is calculated so that this becomes a O(N^2) problem. Since Minecraft's sea world is flat, there is no need to calculate the Y axis when it can be tracked internally. Threads are shot off upon a sea raise or sea lowering event and edit the world on their own thread  in order to avoid the game's internal server from updating slowly. There is one daemon thread that runs around the edge of the players Render Distance (Chunks in this case) making sure that the player can never see the effect break. The daemon thread then sleeps slightly less than the players ablity to run over a chunk at the absolute maximum speed. Then wakes and maintains the effect.

# Modding API

This mod uses the FORGE API version 1.8 for event triggers, which can be found here: [http://files.minecraftforge.net/](http://files.minecraftforge.net/)

# Gameplay
[ISSM Mod Gameplay](https://youtu.be/SM0dxJYghGM)

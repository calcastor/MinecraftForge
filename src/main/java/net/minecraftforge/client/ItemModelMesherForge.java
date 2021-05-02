package net.minecraftforge.client;

import java.util.Map;

import com.google.common.collect.Maps;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.Item;

/**
 * Wrapper around ItemModeMesher that cleans up the internal maps to respect ID remapping.
 */
public class ItemModelMesherForge extends ItemModelMesher
{
    final Map<Item, Int2ObjectMap<ModelResourceLocation>> locations = Maps.newIdentityHashMap();
    final Map<Item, Int2ObjectMap<IBakedModel>> models = Maps.newIdentityHashMap();

    public ItemModelMesherForge(ModelManager manager)
    {
        super(manager);
    }

    protected IBakedModel getItemModel(Item item, int meta)
    {
        Int2ObjectMap<IBakedModel> map = models.get(item);
        return map == null ? null : map.get(meta);
    }

    public void register(Item item, int meta, ModelResourceLocation location)
    {
        Int2ObjectMap<ModelResourceLocation> locs = locations.get(item);
        Int2ObjectMap<IBakedModel>           mods = models.get(item);
        if (locs == null)
        {
            locs = new Int2ObjectOpenHashMap<>();
            locations.put(item, locs);
        }
        if (mods == null)
        {
            mods = new Int2ObjectOpenHashMap<>();
            models.put(item, mods);
        }
        locs.put(meta, location);
        mods.put(meta, getModelManager().getModel(location));
    }

    public void rebuildCache()
    {
        final ModelManager manager = this.getModelManager();
        for (Map.Entry<Item, Int2ObjectMap<ModelResourceLocation>> e : locations.entrySet())
        {
            Int2ObjectMap<IBakedModel> mods = models.get(e.getKey());
            if (mods != null)
            {
                mods.clear();
            }
            else
            {
                mods = new Int2ObjectOpenHashMap<>();
                models.put(e.getKey(), mods);
            }
            final Int2ObjectMap<IBakedModel> map = mods;
            for (Int2ObjectMap.Entry<ModelResourceLocation> entry : e.getValue().int2ObjectEntrySet()) {
                map.put(entry.getIntKey(), manager.getModel(entry.getValue()));
            }
        }
    }
}

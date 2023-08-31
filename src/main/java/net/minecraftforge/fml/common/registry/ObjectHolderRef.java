package net.minecraftforge.fml.common.registry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;

import com.google.common.base.Throwables;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;


/**
 * Internal class used in tracking {@link ObjectHolder} references
 *
 * @author cpw
 */
class ObjectHolderRef {
    private Field field;
    private ResourceLocation injectedObject;
    private boolean isBlock;
    private boolean isItem;


    ObjectHolderRef(Field field, ResourceLocation injectedObject, boolean extractFromExistingValues) {
        this.field = field;
        this.isBlock = Block.class.isAssignableFrom(field.getType());
        this.isItem = Item.class.isAssignableFrom(field.getType());
        if (extractFromExistingValues) {
            try {
                Object existing = field.get(null);
                // nothing is ever allowed to replace AIR
                if (existing == null || existing == GameData.getBlockRegistry().getDefaultValue()) {
                    this.injectedObject = null;
                    this.field = null;
                    this.isBlock = false;
                    this.isItem = false;
                    return;
                } else {
                    ResourceLocation tmp = isBlock ? GameData.getBlockRegistry().getNameForObject((Block) existing) :
                            isItem ? GameData.getItemRegistry().getNameForObject((Item) existing) : null;
                    this.injectedObject = tmp;
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        } else {
            this.injectedObject = injectedObject;
        }

        if (this.injectedObject == null || !isValid()) {
            throw new IllegalStateException(String.format("The ObjectHolder annotation cannot apply to a field that is not an Item or Block (found : %s at %s.%s)", field.getType().getName(), field.getClass().getName(), field.getName()));
        }
        makeWritable(field);
    }

    private static Field modifiersField;
    private static Object reflectionFactory;
    private static Method newFieldAccessor;
    private static Method fieldAccessorSet;
    private MethodHandle fieldSetter = null;

    private static void makeWritable(Field f) {
        try {
            f.setAccessible(true);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public boolean isValid() {
        return isBlock || isItem;
    }

    public void apply() {
        if (fieldSetter == null) {
            try {
                fieldSetter = MethodHandles.lookup().unreflectSetter(this.field);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        Object thing;
        if (isBlock) {
            thing = GameData.getBlockRegistry().getObject(injectedObject);
            if (thing == Blocks.air) {
                thing = null;
            }
        } else if (isItem) {
            thing = GameData.getItemRegistry().getObject(injectedObject);
        } else {
            thing = null;
        }

        if (thing == null) {
            FMLLog.getLogger().log(
                    Level.DEBUG,
                    "Unable to lookup {} for {}. This means the object wasn't registered. It's likely just mod options.",
                    injectedObject,
                    field);
            return;
        }
        try {
            fieldSetter.invoke(thing);
            FMLLog.finer("Set field " + field.toString() + " to " + thing);
        } catch (Throwable e) {
            FMLLog.log(Level.WARN, e, "Unable to set %s with value %s (%s)", this.field, thing, this.injectedObject);
        }
    }
}

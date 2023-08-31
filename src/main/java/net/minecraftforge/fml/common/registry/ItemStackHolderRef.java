package net.minecraftforge.fml.common.registry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLLog;

import org.apache.logging.log4j.Level;

import com.google.common.base.Throwables;


/**
 * Internal class used in tracking {@link ItemStackHolder} references
 *
 * @author cpw
 */
class ItemStackHolderRef {
    private Field field;
    private String itemName;
    private int meta;
    private String serializednbt;


    ItemStackHolderRef(Field field, String itemName, int meta, String serializednbt) {
        this.field = field;
        this.itemName = itemName;
        this.meta = meta;
        this.serializednbt = serializednbt;
        makeWritable(field);
    }

    private static Field modifiersField;
    private static Object reflectionFactory;
    private static Method newFieldAccessor;
    private static Method fieldAccessorSet;
    private static MethodHandle fieldSetter;

    private static void makeWritable(Field f) {
        try {
            f.setAccessible(true);
            fieldSetter = MethodHandles.lookup().unreflectSetter(f);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public void apply() {
        ItemStack is;
        try {
            is = GameRegistry.makeItemStack(itemName, meta, 1, serializednbt);
        } catch (RuntimeException e) {
            FMLLog.getLogger().log(
                    Level.ERROR,
                    "Caught exception processing itemstack {},{},{} in annotation at {}.{}",
                    itemName,
                    meta,
                    serializednbt,
                    field.getClass().getName(),
                    field.getName());
            throw e;
        }
        try {
            fieldSetter.invoke(is);
        } catch (Throwable e) {
            FMLLog.getLogger().log(
                    Level.WARN,
                    "Unable to set {} with value {},{},{}",
                    this.field,
                    this.itemName,
                    this.meta,
                    this.serializednbt);
        }
    }
}

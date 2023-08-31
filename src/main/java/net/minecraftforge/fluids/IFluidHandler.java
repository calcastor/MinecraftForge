package net.minecraftforge.fluids;

import net.minecraft.util.EnumFacing;

/**
 * Implement this interface on TileEntities which should handle fluids, generally storing them in
 * one or more internal {@link IFluidTank} objects.
 * <p>
 * A reference implementation is provided {@link TileFluidHandler}.
 *
 * @author King Lemming
 */
public interface IFluidHandler {
    /**
     * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param from     Orientation the Fluid is pumped in from.
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be filled.
     * @param doFill   If false, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    int fill(EnumFacing from, FluidStack resource, boolean doFill);

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param from     Orientation the Fluid is drained to.
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain);

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     * <p>
     * This method is not Fluid-sensitive.
     *
     * @param from     Orientation the fluid is drained to.
     * @param maxDrain Maximum amount of fluid to drain.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain);

    /**
     * Returns true if the given fluid can be inserted into the given direction.
     * <p>
     * More formally, this should return true if fluid is able to enter from the given direction.
     */
    boolean canFill(EnumFacing from, Fluid fluid);

    /**
     * Returns true if the given fluid can be extracted from the given direction.
     * <p>
     * More formally, this should return true if fluid is able to leave from the given direction.
     */
    boolean canDrain(EnumFacing from, Fluid fluid);

    /**
     * Returns an array of objects which represent the internal tanks. These objects cannot be used
     * to manipulate the internal tanks. See {@link FluidTankInfo}.
     *
     * @param from Orientation determining which tanks should be queried.
     * @return Info for the relevant internal tanks.
     */
    FluidTankInfo[] getTankInfo(EnumFacing from);
}

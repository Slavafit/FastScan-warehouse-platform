export const DEFAULT_VOLUMETRIC_DIVISOR = 6000;

export type WeightBreakdown = {
  gross_weight: number | null;
  volume_weight: number | null;
  chargeable_weight: number | null;
};

export function calculateWeightBreakdown(
  grossWeight: number | null,
  length: number | null,
  width: number | null,
  height: number | null,
  divisor = DEFAULT_VOLUMETRIC_DIVISOR,
): WeightBreakdown {
  const validDivisor = divisor > 0 ? divisor : DEFAULT_VOLUMETRIC_DIVISOR;
  const hasDimensions =
    length != null && length > 0 &&
    width != null && width > 0 &&
    height != null && height > 0;
  const volumeWeight = hasDimensions
    ? roundWeight((length * width * height) / validDivisor)
    : null;
  const normalizedGross = grossWeight == null ? null : roundWeight(grossWeight);
  const chargeableWeight = normalizedGross == null
    ? volumeWeight
    : volumeWeight == null
      ? normalizedGross
      : roundWeight(Math.max(normalizedGross, volumeWeight));
  return {
    gross_weight: normalizedGross,
    volume_weight: volumeWeight,
    chargeable_weight: chargeableWeight,
  };
}

export function roundWeight(value: number) {
  return Math.round(value * 1000) / 1000;
}

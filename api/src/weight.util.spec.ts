import { calculateWeightBreakdown } from './weight.util.js';

describe('weight calculations', () => {
  it('uses volumetric weight for a light but bulky shipment', () => {
    expect(calculateWeightBreakdown(5, 60, 40, 40, 6000)).toEqual({
      gross_weight: 5,
      volume_weight: 16,
      chargeable_weight: 16,
    });
  });

  it('uses actual weight when it is greater', () => {
    expect(calculateWeightBreakdown(20, 60, 40, 40, 6000)).toEqual({
      gross_weight: 20,
      volume_weight: 16,
      chargeable_weight: 20,
    });
  });

  it('falls back to actual weight when dimensions are missing', () => {
    expect(calculateWeightBreakdown(3.25, null, null, null, 6000)).toEqual({
      gross_weight: 3.25,
      volume_weight: null,
      chargeable_weight: 3.25,
    });
  });
});

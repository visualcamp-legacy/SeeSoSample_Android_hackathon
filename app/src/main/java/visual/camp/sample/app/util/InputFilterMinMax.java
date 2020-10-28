package visual.camp.sample.app.util;

import android.text.InputFilter;
import android.text.Spanned;

public class InputFilterMinMax implements InputFilter {

        private Number min, max;

        public InputFilterMinMax(Number min, Number max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                if (min instanceof Byte) {
                    byte input = Byte.parseByte(dest.toString() + source.toString());
                    if (isInRange((byte)min, (byte)max, input)) {
                        return null;
                    }
                } else if (min instanceof Short) {
                    short input = Short.parseShort(dest.toString() + source.toString());
                    if (isInRange((short)min, (short)max, input)) {
                        return null;
                    }
                } else if (min instanceof Integer) {
                    int input = Integer.parseInt(dest.toString() + source.toString());
                    if (isInRange((int)min, (int)max, input)) {
                        return null;
                    }
                } else if (min instanceof Long) {
                    long input = Long.parseLong(dest.toString() + source.toString());
                    if (isInRange((long)min, (long)max, input)) {
                        return null;
                    }
                } else if (min instanceof Float) {
                    float input = Float.parseFloat(dest.toString() + source.toString());
                    if (isInRange((float)min, (float)max, input)) {
                        return null;
                    }
                } else if (min instanceof Double) {
                    double input = Double.parseDouble(dest.toString() + source.toString());
                    if (isInRange((double)min, (double)max, input)) {
                        return null;
                    }
                }
            } catch (NumberFormatException nfe) { }
            return "";
        }
        private boolean isInRange(byte a, byte b, byte c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
        private boolean isInRange(short a, short b, short c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
        private boolean isInRange(int a, int b, int c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
        private boolean isInRange(long a, long b, long c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
        private boolean isInRange(float a, float b, float c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
        private boolean isInRange(double a, double b, double c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }

    }
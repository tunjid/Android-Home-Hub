/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.nsd.protocols;


/**
 * Provides methods to convert colours from RGB to CIE colour space.
 * <p>
 * The CIE XYZ color space encompasses all color sensations that an average
 * person can experience. It serves as a standard reference against which many
 * other color spaces are defined.
 */
public class Cie {
    public double x;
    public double y;
    public double Y;

    public static Cie rgb2cie(double r, double g, double b) {
        double X;
        double Y;
        double Z;

        if (r <= 0.04045) {
            r = r / 12;
        }
        else {
            r = (float) Math.pow((r + 0.055) / 1.055, 2.4);
        }

        if (g <= 0.04045) {
            g = g / 12;
        }
        else {
            g = (float) Math.pow((g + 0.055) / 1.055, 2.4);
        }

        if (b <= 0.04045) {
            b = b / 12;
        }
        else {
            b = (float) Math.pow((b + 0.055) / 1.055, 2.4);
        }

        X = 0.436052025f * r + 0.385081593f * g + 0.143087414f * b;
        Y = 0.222491598f * r + 0.71688606f * g + 0.060621486f * b;
        Z = 0.013929122f * r + 0.097097002f * g + 0.71418547f * b;

        double x;
        double y;

        double sum = X + Y + Z;
        if (sum != 0) {
            x = X / sum;
            y = Y / sum;
        }
        else {
            float Xr = 0.964221f; // reference white
            float Yr = 1.0f;
            float Zr = 0.825211f;

            x = Xr / (Xr + Yr + Zr);
            y = Yr / (Xr + Yr + Zr);
        }
        Cie cie = new Cie();
        cie.x = x;
        cie.y = y;
        cie.Y = Y;
        return cie;
    }
}

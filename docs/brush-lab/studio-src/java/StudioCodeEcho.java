package io.github.mpdairy.monopaint;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Brush Studio's code check: each line an M code; prints the app's reading of it written again, or "null". */
public final class StudioCodeEcho {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) {
            ModularBrush b = ModularCode.decode(line);
            out.append(b == null ? "null" : ModularCode.encode(b)).append('\n');
        }
        System.out.print(out);
    }
}

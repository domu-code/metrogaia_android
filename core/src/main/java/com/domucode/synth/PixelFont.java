package com.domucode.synth;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.HashMap;
import java.util.Map;

/** PocketTracker 3 R15 5x5 bitmap glyphs from native/ui/font5x5.h.
 * This source-rendered font does not need a TTF file or Android system font.
 */
public final class PixelFont {
    private static final Map<Character,int[]> GLYPHS=new HashMap<>();
    static {
        add('0',"11111 10001 10101 10001 11111"); add('1',"01100 10100 00100 00100 11111");
        add('2',"11110 00001 00110 01000 11111"); add('3',"11111 00001 01111 00001 11111");
        add('4',"10001 10001 11111 00001 00001"); add('5',"11111 10000 11111 00001 11111");
        add('6',"11111 10000 11111 10001 11111"); add('7',"11111 00001 00010 00100 01000");
        add('8',"11111 10001 11111 10001 11111"); add('9',"11111 10001 11111 00001 11111");
        add('A',"01110 10001 10001 11111 10001"); add('B',"11110 10001 11110 10001 11110");
        add('C',"01111 10000 10000 10000 01111"); add('D',"11110 10001 10001 10001 11110");
        add('E',"11111 10000 11110 10000 11111"); add('F',"11111 10000 11110 10000 10000");
        add('G',"01111 10000 10111 10001 01111"); add('H',"10001 10001 11111 10001 10001");
        add('I',"11111 00100 00100 00100 11111"); add('J',"00001 00001 10001 10001 01110");
        add('K',"10001 10010 11100 10010 10001"); add('L',"10000 10000 10000 10000 11111");
        add('M',"10001 11011 10101 10101 10001"); add('N',"10001 11001 10101 10011 10001");
        add('O',"01110 10001 10001 10001 01110"); add('P',"11110 10001 11110 10000 10000");
        add('Q',"01110 10001 10001 10010 01101"); add('R',"11110 10001 11110 10010 10001");
        add('S',"01111 10000 01110 00001 11110"); add('T',"11111 00100 00100 00100 00100");
        add('U',"10001 10001 10001 10001 01110"); add('V',"10001 10001 10001 01010 00100");
        add('W',"10001 10101 10101 10101 01110"); add('X',"10001 01010 00100 01010 10001");
        add('Y',"10001 10001 01110 00100 00100"); add('Z',"11111 00010 00100 01000 11111");
        add('_',"00000 00000 00000 00000 11111"); add('-',"00000 00000 11111 00000 00000");
        add('#',"01010 11111 01010 11111 01010"); add('.',"00000 00000 00000 00000 00100");
        add(',',"00000 00000 00000 00100 00100"); add(':',"00000 00100 00000 00100 00000");
        add('/',"00001 00010 00100 01000 10000"); add('%',"10001 00010 00100 01000 10001");
        add('+',"00100 00100 11111 00100 00100"); add('<',"00010 00100 01000 00100 00010");
        add('>',"01000 00100 00010 00100 01000"); add('=',"00000 11111 00000 11111 00000");
        add('[',"00110 00100 00100 00100 00110"); add('(',"00010 00100 00100 00100 00010");
        add('!',"00100 00100 00100 00000 00100"); add('?', "01110 00010 00100 00000 00100");
        add(']',"01100 00100 00100 00100 01100"); add(')',"01000 00100 00100 00100 01000");
        add('|',"00100 00100 00100 00100 00100"); add('"',"01010 00000 00000 00000 00000");
        add('*',"00100 10101 01110 10101 00100"); add(' ',"00000 00000 00000 00000 00000");
    }
    private static void add(char c,String rows){
        String[] r=rows.split(" ");
        int[] bits=new int[5];
        for(int i=0;i<5;i++)bits[i]=Integer.parseInt(r[i],2);
        GLYPHS.put(c,bits);
    }
    private final Paint paint=new Paint();
    public void draw(Canvas canvas,String str,float x,float y,float pixel,int color){
        paint.setColor(color);paint.setStyle(Paint.Style.FILL);
        float x0=x;
        for(int i=0;i<str.length();i++){
            char ch=Character.toUpperCase(str.charAt(i));
            if(ch=='\n'){x=x0;y+=7*pixel;continue;}
            int[] g=GLYPHS.get(ch);
            if(g==null)g=GLYPHS.get('?');
            for(int row=0;row<5;row++)
                for(int col=0;col<5;col++)
                    if((g[row] & (1<<(4-col)))!=0)
                        canvas.drawRect(x+col*pixel,y+row*pixel,x+(col+1)*pixel,y+(row+1)*pixel,paint);
            x+=6*pixel;
        }
    }
    public float width(String s,float pixel){return s.length()*pixel*6;}
}

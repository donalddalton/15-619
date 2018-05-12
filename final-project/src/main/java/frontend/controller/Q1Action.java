package frontend.controller;

import frontend.dao.MySQLDAO;
import frontend.utils.Template;
import io.undertow.server.HttpServerExchange;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Deque;
import java.util.Map;


public class Q1Action extends Action {
    private MySQLDAO mySQLModel;

    public Q1Action(MySQLDAO mySQLModel) { this.mySQLModel = mySQLModel; }
    public Q1Action() {}

    private Template template = new Template();
    private boolean[][] ver1Template = template.ver1Template;
    private boolean[][] ver1Occupied = template.ver1Occupied;
    private boolean[][] ver2Template = template.ver2Template;
    private boolean[][] ver2Occupied = template.ver2Occupied;
    private int version;
    private int end;

    public String handleGet(final HttpServerExchange exchange) {
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        if (!params.containsKey("type") || !params.containsKey("data")) { return "heartbeat"; }
        if (params.get("type").peek().equals("encode"))                 { return encode(params.get("data").poll()); }
        if (params.get("type").peek().equals("decode"))                 { return decode(params.get("data").poll()); }
        return "heartbeat";
    }

    public String handlePost(final HttpServerExchange exchange) { return handleGet(exchange); }

    public String encode(String code) {
        try {
            Timestamp start = new Timestamp(System.currentTimeMillis());

            if (code.length() <= 14) {
                version = 1;
                end = 20;
            } else if (code.length() <= 23) {
                version = 2;
                end = 24;
            } else {
                // throw new java.lang.RuntimeException("The code length can only be uptill 23 characters");
                return "heartbeat";
            }
            byte[] bCodeSubset = code.getBytes(StandardCharsets.US_ASCII);
            byte[] bCode = new byte[code.length() * 2 + 1];
            bCode[0] = (byte) code.length();
            byte[] bParity = new byte[code.length()];
            //Can use a lookup table of size 256 to find out the XOR simply (or faster)
            for (int i = 0; i < code.length(); i++) {
                for (int j = 0; j < 8; j++) {
                    bParity[i] = (byte) (bParity[i] ^ ((bCodeSubset[i] >>> j) & 0b1));
                }
            }
            //Copy into bCode in the right order
            for (int i = 1; i < (code.length() * 2 + 1); i++) {
                if (i % 2 == 1) {
                    bCode[i] = bCodeSubset[i / 2];
                } else {
                    bCode[i] = bParity[(i - 1) / 2];
                }
            }

            boolean[][] qr;
            boolean[][] occupied;
            if (version == 1) {
                qr = ver1Template;
                occupied = ver1Occupied;
            } else if (version == 2) {
                qr = ver2Template;
                occupied = ver2Occupied;
            } else {
                //throw new java.lang.RuntimeException("version can only be either 1 or 2");
                return "heartbeat";
            }
            int bitCount = 0;
            int yDirection = -1; //-1 == Go up, 1 == Go down
            int y = end;
            byte[] filler = new byte[2];
            filler[0] = (byte) 0b11101100;
            filler[1] = (byte) 0b00010001;
            int startFiller = 1;
            for (int x = end; x >= 0; x = x - 1) {
                if (y < 0) {
                    y = 0;
                }
                if (y > end) {
                    y = end;
                }
                while (y >= 0 && y <= end) {
                    if (occupied[x][y]) { //|| occupied[x-1][y] == true)) {
                        int ySeek = y + yDirection;
                        if (ySeek > end || ySeek < 0) {
                            break;
                        }
                        while (occupied[x][ySeek] == true) {// || occupied[x-1][ySeek] == true)) {
                            ySeek += yDirection;
                            if (ySeek > end || ySeek < 0) {
                                break;
                            }
                        }
                        y = ySeek;
                        if (y < 0 || y > end) {
                            break;
                        }
                    }
                    byte toWrite;
                    if (bitCount / 8 < bCode.length && startFiller == 1) {
                        byte curByte = bCode[bitCount / 8];
                        toWrite = (byte) (curByte >>> ((7 - (bitCount % 8))) & 0b1);
                    } else {
                        if (startFiller == 1) {
                            bitCount = 0;
                            startFiller = 0;
                        }
                        byte curByte = filler[(bitCount / 8) % 2];
                        toWrite = (byte) (curByte >>> ((7 - (bitCount % 8))) & 0b1);
                    }
                    if (toWrite == 1) {
                        qr[x][y] = true;
                    } else {
                        qr[x][y] = false;
                    }
                    bitCount++;
                    int ySeek = y + yDirection;
                    if (ySeek > end || ySeek < 0) {
                        break;
                    }
                    while (occupied[x][ySeek] == true) {
                        ySeek += yDirection;
                        if (ySeek > end || ySeek < 0) {
                            break;
                        }
                    }
                    y = ySeek;
                    if (y < 0 || y > end) {
                        break;
                    }
                }
                yDirection *= -1;
            }

            bitCount = 0;
            int tempVal = 0;
            String representation = "";
            for (y = 0; y <= end; y++) {
                for (int x = 0; x <= end; x++) {
                    tempVal = (tempVal << 1) + (qr[x][y] ? 1 : 0);
                    if (bitCount % 32 == 31 || ((x == end) && (y == end))) {
                        if ((x == end) && (y == end)) {
                        }
                        String hex = Integer.toHexString(tempVal);
                        hex = "0x" + hex;
                        representation += hex;
                        tempVal = 0;
                    }
                    bitCount++;
                }
            }
            //System.out.println(representation);

            Timestamp end = new Timestamp(System.currentTimeMillis());
            long diff = end.getTime() - start.getTime();
            // System.out.println("encode: " + diff);

            return representation;
        } catch (ArrayIndexOutOfBoundsException e) {
            // e.printStackTrace();
            return "heartbeat";
        }
    }

    static boolean match(boolean[][] matrix,boolean[][]patch,int matx,int maty) {
        for(int i=0;i<7;i++) {
            for(int j=0;j<7;j++) {
                if(matrix[matx+i][maty+j]!=patch[i][j]){
                    return false;
                }
            }
        }
        return true;
    }
    static boolean[][] rotate90(boolean[][] mat){
        boolean[][] temp = new boolean[mat[0].length][mat.length];
        //Transpose first
        for (int i = 0; i < mat.length; i++)
            for (int j = 0; j < mat[0].length; j++)
                temp[j][i] = mat[i][j];
        for (int i=0;i<mat.length;i++)
            for(int j = 0; j < mat.length / 2; j++)
            {
                boolean temp_v = temp[i][j];
                temp[i][j] = temp[i][temp.length - j - 1];
                temp[i][temp.length - j - 1] = temp_v;
            }
        return temp;
    }

    public String decode(String data) {
        Timestamp start = new Timestamp(System.currentTimeMillis());

        boolean[][]matrix =	codeToQr(data,32,false);
        boolean[][] patch =
	/*0*/		{{true ,true ,true ,true ,true ,true ,true},
	/*1*/		{true ,false,false,false,false,false,true },
	/*2*/		{true ,false,true ,true ,true ,false,true },
	/*3*/		{true ,false,true ,true ,true ,false,true },
	/*4*/		{true ,false,true ,true ,true ,false,true },
	/*5*/		{true ,false,false,false,false,false,true },
	/*6*/		{true ,true ,true ,true ,true ,true ,true }};
        int dim =32;
        int matchnum =0;
        int[] stridex1 = {14,-14,-14,14,14,-14,0,0};
        int[] stridey1 = {14,14,-14,-14,0,0,14,-14};
        int[] stridex2 = {18,-18,18,-18,18,-18,0,0};
        int[] stridey2 = {18,18,-18,-18,0,0,18,-18};
        int[] matchx = new int[3];
        int[] matchy = new int[3];
        int ver=0;
        searchpatches:
        for(int x =0; x<dim-6;x++) {
            for(int y =0;y<dim-6;y++) {
                if(!match(matrix,patch,x,y)) {
                    continue;
                }
                matchx[matchnum]=x;
                matchy[matchnum]=y;
                matchnum++;
                //Have a match if reaches here
                for(int i=0;i<8;i++) {
                    if((stridex1[i]+x>=0 && stridex1[i]+x+6<dim)&&(stridey1[i]+y>=0 && stridey1[i]+y+6<dim)) {
                        if(match(matrix,patch,stridex1[i]+x,stridey1[i]+y)) {
                            matchx[matchnum]=x+stridex1[i];
                            matchy[matchnum]=y+stridey1[i];
                            matchnum++;
                        }
                    }

                }
                if(matchnum==3) {ver=1;break searchpatches;}
                else {
                    for(int i=0;i<8;i++) {
                        if((stridex2[i]+x>=0 && stridex2[i]+x+6<dim)&&(stridey2[i]+y>=0 && stridey2[i]+y+6<dim)) {
                            if(match(matrix,patch,stridex2[i]+x,stridey2[i]+y)) {
                                matchx[matchnum]=x+stridex2[i];
                                matchy[matchnum]=y+stridey2[i];
                                matchnum++;
                            }
                        }

                    }
                    if(matchnum==3) {ver=2;break searchpatches;}
                }
                if(matchnum!=3) {
                    //throw new java.lang.RuntimeException("Did not find the 3 position markers");
                    return "heartbeat";
                }
            }
        }
        //		for(int i=0;i<3;i++) {
        //			System.out.format("%d %d ",matchx[i],matchy[i]);
        //		}
        //		System.out.print("\n");
        //Find the missing point for the non existent 4th marker
        int missingx = 0;
        int missingy = 0;
        int[] countArrX = new int[32];
        int[] countArrY = new int[32];
        for(int val:matchx) { countArrX[val]++; }
        for(int val:matchy) { countArrY[val]++; }
        for(int i=0;i<countArrX.length;i++) { if(countArrX[i]==1) {missingx=i;break;} }
        for(int i=0;i<countArrY.length;i++) { if(countArrY[i]==1) {missingy=i;break;} }

        int quadrant =0;
        //quadrant1 test
        int count=0;
        for (int i=0; i<3; i++) { if(matchx[i]<=missingx &&matchy[i]>=missingy) { count++; } }
        if (count==3) {quadrant=1; }
        else {
            count=0;
            for(int i=0;i<3;i++ ) {
                if(matchx[i]>=missingx && matchy[i]>=missingy) {
                    count++;
                }
            }
            if(count==3)quadrant=2;
            else {
                count=0;
                for(int i=0;i<3;i++ ) {
                    if(matchx[i]>=missingx && matchy[i]<=missingy) {
                        count++;
                    }
                }
                if(count==3)quadrant=3;
                else {
                    count=0;
                    for(int i=0;i<3;i++ ) {
                        if(matchx[i]<=missingx && matchy[i]<=missingy) {
                            count++;
                        }
                    }
                    if(count==3)quadrant=4;
                }
            }
        }
        if(ver==1)dim=21;
        if(ver==2)dim=25;
        //System.out.println("version "+ver);
        boolean[][] cropped =new boolean[dim][dim];
        int[] boundsX= {40,-1};
        int[] boundsY= {40,-1};
        for(int val:matchx) {
            if(val>boundsX[1])boundsX[1]=val;
            if(val<boundsX[0])boundsX[0]=val;
        }
        for(int val:matchy) {
            if(val>boundsY[1])boundsY[1]=val;
            if(val<boundsY[0])boundsY[0]=val;
        }
        boundsX[1]=boundsX[1]+7;
        boundsY[1]=boundsY[1]+7;
        //Cropping the matrix
        //System.out.println("quadrant "+quadrant);
        for(int x=boundsX[0];x<boundsX[1];x++)
            for(int y= boundsY[0];y<boundsY[1];y++)
                cropped[x-boundsX[0]][y-boundsY[0]]=matrix[x][y];
        if(quadrant==1) {
            cropped = rotate90(cropped);
            cropped = rotate90(cropped);
            cropped = rotate90(cropped);
        }
        if(quadrant==2) {
            cropped = rotate90(cropped);
            cropped = rotate90(cropped);
        }
        if(quadrant ==3){
            cropped = rotate90(cropped);
        }
        //printQr(cropped);
        boolean[][]occupied = (ver==1?ver1Occupied:ver2Occupied);
        int y=dim-1;
        byte curByte=0b0;
        int bitCount=0;
        String str="";
        boolean firstbyte=true;
        int yDir = -1;
        int parity=0;
        //printQr(occupied);
        for(int x = dim-1;x>=0;x--) {
            if(y>dim-1)y=dim-1;
            else if(y<0)y=0;
            while(y>=0 && y<=dim-1) {
                //System.out.format("%d %d ", x,y);
                //System.out.println(occupied[x][y]);
                if(occupied[x][y]==false) {
                    curByte=(byte) ((curByte<<1)+(cropped[x][y]?0b1:0b0));
                    bitCount++;

                    if(bitCount==8) {
                        bitCount=0;
                        if(firstbyte) {
                            firstbyte=false;
                        }
                        else if(parity==1)parity =0;
                        else if(curByte!=-20 && curByte!=17 && parity==0) {
                            str+=Character.toString ((char) curByte);
                            parity=1;
                        }
                        curByte=0b0;
                    }

                }
                y+=yDir;
            }
            yDir*=-1;
        }
        //System.out.println(str);

        Timestamp end = new Timestamp(System.currentTimeMillis());
        long diff = end.getTime() - start.getTime();
        // System.out.println("decode: " + diff);

        return str;
    }
    private static boolean[][] codeToQr(String code,int dimension,boolean print) {
        boolean[][] qr = new boolean[dimension][dimension];
        String[] hexes=code.split("0x");
        int bitCount=0;
        int end=31;
        int y = 0;
        for(int z=0;z<hexes.length;z++) {
            String hex = hexes[z];
            if(hex.equals("")) {
                continue;
            }
            //System.out.println(hex);
            Long n = Long.parseLong(hex, 16);
            if(z==hexes.length-1) {
                end=(dimension*dimension-bitCount)-1;
            }
            for(int i=end;i>=0;i--) {
                if(((n>>i)&1)==1) {
                    if(print==true) {
                        System.out.print("\u25A0");
                    }
                    qr[bitCount%dimension][y]=true;
                }
                else {
                    if(print==true) {
                        System.out.print("\u25A1");
                    }
                    qr[bitCount%dimension][y]=false;
                }
                bitCount++;
                if(bitCount%dimension==0) {
                    if(print==true) {
                        System.out.print("\n");
                    }
                    y++;
                }
            }
        }
        if(print==true) {
            System.out.print("\n");
        }
        return qr;
    }
}


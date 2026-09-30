package net.minecraft.world.level.levelgen;

import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.NativeDensityProgram;
import net.minecraft.world.level.levelgen.synth.NativeDensityProgram.Node;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCellDensityTest {
    private static Node node(int op,int a,int b,int c,double p,double q) {return new Node(op,a,b,c,-1,p,q,0);}
    private static NativeDensityProgram program(Node... nodes) {return new NativeDensityProgram(nodes,new NormalNoise[0],0,2);}
    private static void same(double expected,double actual) {assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(actual));}
    private static double lerp(double[] v,int at,double x,double y,double z) {
        return Mth.lerp3(x,y,z,v[at],v[at+1],v[at+2],v[at+3],v[at+4],v[at+5],v[at+6],v[at+7]);
    }
    @Test void cellArithmeticMatchesJavaIncludingExceptionalValues() {
        var p=program(node(24,0,0,0,0,0),node(24,1,0,0,0,0),node(14,1,0,0,0,0),
            node(12,2,0,0,1.5,0),node(8,0,3,0,0,0),node(20,4,0,0,0,0));
        var random=new Random(763511);var corners=new double[16];
        for(int i=0;i<100000;i++) {
            for(int j=0;j<16;j++)corners[j]=i%2==0?Double.longBitsToDouble(random.nextLong()):random.nextDouble()*4-2;
            double x=random.nextDouble(),y=random.nextDouble(),z=random.nextDouble();
            double a=lerp(corners,0,x,y,z)+Math.abs(lerp(corners,8,x,y,z))*1.5;
            a=Mth.clamp(a,-1,1);same(a/2.-a*a*a/24.,p.cell(corners,x,y,z));
        }
    }
    @Test void batchPreservesSkippedTailAndDirectFillIndex() {
        var corners=new double[16];for(int i:new int[]{2,3,6,7})corners[i]=1.;
        var multiply=program(node(24,0,0,0,0,0),node(0,0,0,0,2,0),node(9,0,1,0,0,0));
        var direct=program(node(24,0,0,0,0,0),node(15,0,0,0,0,0));
        for(int width:new int[]{1,3,4,8})for(int height:new int[]{2,7,65}) {
            int count=width*width*height;double[] values=new double[count];
            long trace=multiply.cellArray(corners,values,width,height);
            assertEquals(count-width*width-1,(int)trace);assertEquals(6,trace>>>32);
            for(int i=0;i<count;i++)same(2.*(double)(height-1-i/(width*width))/height,values[i]);
            trace=direct.cellArray(corners,values,width,height);
            assertEquals(count-1,(int)trace);assertEquals(3,trace>>>32);
        }
    }
    @Test void rangeUsesComputeTraversalEvenWithDirectFillChild() {
        var p=program(node(0,0,0,0,0,0),node(24,0,0,0,0,0),node(24,1,0,0,0,0),node(22,0,1,2,-1,1));
        assertEquals((8L<<32)|511,p.cellArray(new double[16],new double[512],8,8));
    }
    @Test void invalidCellProgramsAndBuffersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->program(node(24,2,0,0,0,0)).cell(new double[16],0,0,0));
        // Shared AST children would not have a unique traversal phase.
        assertThrows(IllegalArgumentException.class,()->program(node(24,0,0,0,0,0),node(8,0,0,0,0,0)).cell(new double[16],0,0,0));
        var p=program(node(24,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->p.cell(new double[8],0,0,0));
        assertThrows(IllegalArgumentException.class,()->p.cellArray(new double[16],new double[7],2,2));
        var same=new double[16];assertThrows(IllegalArgumentException.class,()->p.cellArray(same,same,2,4));
    }
    private record Expression(int op,Expression a,Expression b,Expression c,double p,double q) {
        int emit(java.util.List<Node> nodes) {
            int ia=a==null?0:a.emit(nodes),ib=b==null?0:b.emit(nodes),ic=c==null?0:c.emit(nodes);
            nodes.add(node(op,op==24?(int)p:ia,ib,ic,p,q));return nodes.size()-1;
        }
        double apply(double x,double y) {
            return switch(op) {
                case 8->x+y;case 9->x==0.?0.:x*y;case 10->x<p?x:Math.min(x,y);case 11->x>q?x:Math.max(x,y);
                case 12->x*p;case 13->x+p;case 14->Math.abs(x);case 15->x*x;case 16->x*x*x;
                case 17->x>0.?x:x*.5;case 18->x>0.?x:x*.25;case 19->1./x;
                case 20->{double v=Mth.clamp(x,-1.,1.);yield v/2.-v*v*v/24.;}
                case 21->Mth.clamp(x,p,q);default->throw new AssertionError();
            };
        }
        boolean right(double v){return switch(op){case 9->v!=0.;case 10->!(v<p);case 11->!(v>q);default->true;};}
        double compute(double[] corners,int i,int w,int h) {
            if(op==0)return p;
            if(op==24)return lerp(corners,(int)p*8,(double)((i/w)%w)/w,(double)(h-1-i/(w*w))/h,(double)(i%w)/w);
            double x=a.compute(corners,i,w,h);
            if(op==22)return (x>=p && x<q?b:c).compute(corners,i,w,h);
            return apply(x,b!=null && right(x)?b.compute(corners,i,w,h):0.);
        }
        void fill(double[] values,double[] corners,int w,int h,int[] visit) {
            if(op==0){java.util.Arrays.fill(values,p);return;}
            if(op==24) {
                for(int i=0;i<values.length;i++)values[i]=compute(corners,i,w,h);
                visit[0]=values.length-1;visit[1]=values.length;return;
            }
            a.fill(values,corners,w,h,visit);
            if(op==8) {
                double[] rhs=new double[values.length];b.fill(rhs,corners,w,h,visit);
                for(int i=0;i<values.length;i++)values[i]+=rhs[i];return;
            }
            for(int i=0;i<values.length;i++) {
                double x=values[i];
                if(op==22) {
                    visit[0]=i;visit[1]=i;values[i]=(x>=p && x<q?b:c).compute(corners,i,w,h);
                } else if(op>=9 && op<=11) {
                    double y=0.;if(right(x)){visit[0]=i;visit[1]=i;y=b.compute(corners,i,w,h);}
                    values[i]=apply(x,y);
                } else values[i]=apply(x,0.);
            }
        }
    }
    private static Expression randomExpression(Random r,int depth) {
        if(depth==0 || r.nextInt(5)==0)return r.nextBoolean()?new Expression(24,null,null,null,r.nextInt(2),0.)
            :new Expression(0,null,null,null,r.nextBoolean()?0.:r.nextDouble()*2.-1.,0.);
        int op=8+r.nextInt(15);double p=op==10?Double.NEGATIVE_INFINITY:-0.3,q=op==11?Double.POSITIVE_INFINITY:0.7;
        return new Expression(op,randomExpression(r,depth-1),op<=11 || op==22?randomExpression(r,depth-1):null,
            op==22?randomExpression(r,depth-1):null,p,q);
    }
    @Test void randomTreesMatchJavaArrayResultsAndFinalProviderState() {
        var random=new Random(267116);int width=3,height=31,count=width*width*height;
        for(int run=0;run<500;run++) {
            var expression=randomExpression(random,3);var nodes=new java.util.ArrayList<Node>();expression.emit(nodes);
            var p=program(nodes.toArray(Node[]::new));var corners=new double[16];
            for(int j=0;j<16;j++)corners[j]=run%3==0?Double.longBitsToDouble(random.nextLong()):random.nextDouble()*2.-1.;
            var expected=new double[count];var actual=new double[count];int[] visit={-1,-1};
            expression.fill(expected,corners,width,height,visit);
            long trace=p.cellArray(corners,actual,width,height);
            for(int i=0;i<count;i++)same(expected[i],actual[i]);
            assertEquals(visit[0],trace<0?-1:(int)trace);
            assertEquals(visit[1],trace<0?-1:(int)trace+(int)((trace>>>32)&1));
        }
    }
    @Test void specializedTerrainShapeMatchesJavaAndTraversal() {
        var random=new Random(21795);
        for(int run=0;run<1000;run++) {
            int width=1+run%8,height=1+(run*7)%67,count=width*width*height;
            int s0=random.nextInt(5),s1=random.nextInt(5),s2=random.nextInt(5),s3=random.nextInt(5),s4=random.nextInt(5);
            double baseScale=run%3==0?0.64:random.nextDouble()*2.-1.;
            double ridgeScale=run%3==0?1.5:random.nextDouble()*2.-1.;
            double ridgeBound=run%3==0?3.333333333333333:random.nextDouble();
            double minBound=run%3==0?-0.15833333333333333:random.nextDouble()*2.-1.;
            double lo=run%3==0?-1000000.:random.nextDouble()*2.-1.,hi=run%3==0?0.:random.nextDouble()*2.-1.;
            double constant=run%3==0?64.:random.nextDouble()*2.-1.;
            Node[] nodes={node(24,s0,0,0,0,0),node(12,0,0,0,baseScale,0),node(20,1,0,0,0,0),
                node(24,s1,0,0,0,0),node(0,0,0,0,constant,0),node(24,s2,0,0,0,0),node(24,s3,0,0,0,0),
                node(14,6,0,0,0,0),node(24,s4,0,0,0,0),node(14,8,0,0,0,0),node(11,7,9,0,0,ridgeBound),
                node(12,10,0,0,ridgeScale,0),node(8,5,11,0,0,0),node(22,3,4,12,lo,hi),node(10,2,13,0,minBound,0)};
            boolean addTail=run%2!=0;double tail=run%3==0?-0.:run%5==0?Double.NaN:0.;
            if(addTail) {
                nodes=java.util.Arrays.copyOf(nodes,17);nodes[15]=node(0,0,0,0,tail,0);nodes[16]=node(8,14,15,0,0,0);
            }
            var p=new NativeDensityProgram(nodes,new NormalNoise[0],0,5);double[] corners=new double[40],values=new double[count];
            for(int j=0;j<40;j++)corners[j]=run%4==0?Double.longBitsToDouble(random.nextLong()):random.nextDouble()*2.-1.;
            long trace=p.cellArray(corners,values,width,height);int last=-1;
            for(int i=0;i<count;i++) {
                double x=(double)((i/width)%width)/width,y=(double)(height-1-i/(width*width))/height,z=(double)(i%width)/width;
                double a=Mth.clamp(lerp(corners,s0*8,x,y,z)*baseScale,-1.,1.);a=a/2.-a*a*a/24.;double expected=a;
                if(!(a<minBound)) {
                    last=i;double toggle=lerp(corners,s1*8,x,y,z),b=constant;
                    if(!(toggle>=lo && toggle<hi)) {
                        double thickness=lerp(corners,s2*8,x,y,z),ridge=Math.abs(lerp(corners,s3*8,x,y,z));
                        if(!(ridge>ridgeBound))ridge=Math.max(ridge,Math.abs(lerp(corners,s4*8,x,y,z)));
                        b=thickness+ridge*ridgeScale;
                    }
                    expected=Math.min(a,b);
                }
                same(addTail?expected+tail:expected,values[i]);
            }
            assertEquals(last<0?count-1:last,(int)trace);assertEquals(last<0?3:30,trace>>>32);
        }
    }
    @Test void singleInterpolatorKernelMatchesJavaAcrossCellShapes() {
        var random=new Random(211754);
        for(int run=0;run<1000;run++) {
            int width=1+run%8,height=1+(run*7)%67,count=width*width*height;
            double factor=run%2==0?0.64:Double.longBitsToDouble(random.nextLong());
            boolean addTail=run%2!=0;double tail=run%3==0?-0.:run%5==0?Double.NaN:0.;
            Node[] nodes={node(24,1,0,0,0,0),node(12,0,0,0,factor,0),node(20,1,0,0,0,0)};
            if(addTail) {nodes=java.util.Arrays.copyOf(nodes,5);nodes[3]=node(0,0,0,0,tail,0);nodes[4]=node(8,2,3,0,0,0);}
            var p=program(nodes);
            double[] corners=new double[16],values=new double[count];
            for(int j=0;j<16;j++)corners[j]=run%3==0?Double.longBitsToDouble(random.nextLong()):random.nextDouble()*2.-1.;
            assertEquals((3L<<32)|(count-1L),p.cellArray(corners,values,width,height));
            for(int i=0;i<count;i++) {
                double a=lerp(corners,8,(double)((i/width)%width)/width,(double)(height-1-i/(width*width))/height,(double)(i%width)/width)*factor;
                a=Mth.clamp(a,-1.,1.);a=a/2.-a*a*a/24.;same(addTail?a+tail:a,values[i]);
            }
        }
    }
    @Test void wideCellRowsPreserveSimdLanesAndExceptionalValues() {
        double[] exceptional={0.,-0.,Double.MIN_VALUE,-Double.MIN_VALUE,Double.MAX_VALUE,
            -Double.MAX_VALUE,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN,1.,-1.};
        for(int width:new int[]{9,16,17,31,32,33,63,64})for(int height:new int[]{1,3})for(int run=0;run<exceptional.length;run++) {
            double factor=run%2==0?0.64:exceptional[run];
            double tail=run%3==0?-0.:0.;
            Node[] nodes={node(24,0,0,0,0,0),node(12,0,0,0,factor,0),node(20,1,0,0,0,0),
                node(0,0,0,0,tail,0),node(8,2,3,0,0,0)};
            var p=program(nodes);double[] corners=new double[16],values=new double[width*width*height];
            for(int j=0;j<8;j++)corners[j]=run<2?exceptional[run]:exceptional[(run+j)%exceptional.length];
            assertEquals((3L<<32)|(values.length-1L),p.cellArray(corners,values,width,height));
            for(int i=0;i<values.length;i++) {
                double a=lerp(corners,0,(double)((i/width)%width)/width,(double)(height-1-i/(width*width))/height,(double)(i%width)/width)*factor;
                a=Mth.clamp(a,-1.,1.);same(a/2.-a*a*a/24.+tail,values[i]);
            }
        }
    }
}

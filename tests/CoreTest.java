import dev.gachahub.core.ResourceMath;
import java.util.*;
import java.time.Instant;
public class CoreTest {
 static int checks=0;
 static void ok(boolean v){checks++;if(!v)throw new AssertionError("Check "+checks);}
 static void fails(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException|ArithmeticException e){return;}throw new AssertionError("Expected failure "+checks);}
 public static void main(String[] args){
  ok(ResourceMath.missing(46,31)==15);ok(ResourceMath.missing(46,90)==0);ok(ResourceMath.missing(0,0)==0);
  fails(()->ResourceMath.missing(-1,0));fails(()->ResourceMath.missing(1,-1));
  ok(ResourceMath.sum(List.of(Map.of("coin",10L),Map.of("coin",20L,"boss",2L))).equals(Map.of("coin",30L,"boss",2L)));
  fails(()->ResourceMath.sum(List.of(Map.of("coin",Long.MAX_VALUE),Map.of("coin",1L))));
  fails(()->ResourceMath.sum(List.of(Map.of("coin",-1L))));
  ok(ResourceMath.progress(Map.of("coin",1000000L,"boss",10L),Map.of("coin",1000000L))==.5);
  ok(ResourceMath.progress(Map.of(),Map.of())==1);ok(ResourceMath.progress(Map.of("boss",10L),Map.of("boss",30L))==1);
  var allocation=ResourceMath.allocate(List.of(Map.of("boss",30L),Map.of("boss",30L)),Map.of("boss",46L));
  ok(allocation.get(0).get("boss")==30);ok(allocation.get(1).get("boss")==16);
  ok(ResourceMath.allocate(List.of(Map.of("boss",3L)),Map.of()).get(0).get("boss")==0);
  fails(()->ResourceMath.allocate(List.of(Map.of("boss",-1L)),Map.of()));
  ok(ResourceMath.compare(3000,2080,2600,3000).equals("Excelente"));
  ok(ResourceMath.compare(2600,2080,2600,3000).equals("Adequado"));
  ok(ResourceMath.compare(2300,2080,2600,3000).equals("Precisa melhorar"));
  ok(ResourceMath.compare(1000,2080,2600,3000).equals("Muito abaixo"));
  fails(()->ResourceMath.compare(Double.NaN,1,2,3));fails(()->ResourceMath.compare(1,3,2,4));
  ok(ResourceMath.estimatedEnergy(7,3,40)==120);ok(ResourceMath.estimatedEnergy(0,3,40)==0);
  fails(()->ResourceMath.estimatedEnergy(2,0,40));fails(()->ResourceMath.estimatedEnergy(2,Double.NaN,40));
  // Monday UTC 08:59 is still Sunday on Americas reset 04:00 UTC-5.
  ok(ResourceMath.serverDay(Instant.parse("2026-09-07T08:59:59Z").getEpochSecond(),-5,4)==7);
  ok(ResourceMath.serverDay(Instant.parse("2026-09-07T09:00:00Z").getEpochSecond(),-5,4)==1);
  ok(ResourceMath.serverDay(Instant.parse("2026-09-06T20:00:00Z").getEpochSecond(),8,4)==1);
  fails(()->ResourceMath.serverDay(0,20,4));fails(()->ResourceMath.serverDay(0,-5,24));
  ok(ResourceMath.cacheFresh(100,90,110));ok(!ResourceMath.cacheFresh(110,90,110));ok(!ResourceMath.cacheFresh(80,90,110));
  ok(ResourceMath.matches("Ária","Ether","DPS","Anomaly","aria ether"));
  ok(!ResourceMath.matches("Ária","Ether","DPS","Anomaly","ice"));
  ok(ResourceMath.matches("Ellen","Ice","DPS","Attack",""));
  Random rng=new Random(42);
  for(int i=0;i<1000;i++){
   long stock=rng.nextInt(10000),n1=rng.nextInt(10000),n2=rng.nextInt(10000);
   var a=ResourceMath.allocate(List.of(Map.of("x",n1),Map.of("x",n2)),Map.of("x",stock));
   long used=a.get(0).get("x")+a.get(1).get("x");
   ok(used<=stock && a.get(0).get("x")<=n1 && a.get(1).get("x")<=n2);
   ok(ResourceMath.progress(Map.of("x",n1),a.get(0))>=0 && ResourceMath.progress(Map.of("x",n1),a.get(0))<=1);
  }
  System.out.println("PASS: "+checks+" production-domain assertions");
 }
}

package android.os;
import java.util.Comparator;
import java.util.PriorityQueue;
/** Deterministic message queue: production timer callbacks run, without sleeping or an Android VM. */
public final class Handler {
    private static long clock,sequence;
    private static final PriorityQueue<Event> QUEUE=new PriorityQueue<>(Comparator.comparingLong((Event e)->e.at).thenComparingLong(e->e.sequence));
    public Handler(Looper looper){}
    public boolean post(Runnable task){return postDelayed(task,0);}
    public boolean postDelayed(Runnable task,long delay){QUEUE.add(new Event(task,clock+delay,sequence++));return true;}
    public void removeCallbacks(Runnable task){QUEUE.removeIf(event->event.task==task);}
    public static long now(){return clock;}
    public static void reset(){QUEUE.clear();clock=sequence=0;}
    public static void advance(long millis){
        long end=clock+millis;int count=0;
        while(!QUEUE.isEmpty()&&QUEUE.peek().at<=end){
            if(++count>10000)throw new AssertionError("Unbounded reconnect callbacks");
            Event event=QUEUE.poll();clock=event.at;event.task.run();
        }
        clock=end;
    }
    private static final class Event {
        final Runnable task;final long at,sequence;
        Event(Runnable task,long at,long sequence){this.task=task;this.at=at;this.sequence=sequence;}
    }
}

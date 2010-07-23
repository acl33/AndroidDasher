package dasher.android;

public class IntQueue {
	private int[] data=new int[4];
	private int front; //next idx to push
	private int back; //next idx to pop
	
	public void push(int i) {
		if ((front+1) % data.length == back) {
			int[] ndata = new int[data.length*2];
			if (front>back) {
				System.arraycopy(data, back, ndata, 0, front-back);
				front-=back;
			} else {
				System.arraycopy(data, back, ndata, 0, data.length-back);
				System.arraycopy(data, 0, ndata, data.length-back, front);
				front += data.length-back;
			}
			data=ndata;
			back=0;
		}
		data[front++]=i;
		if (front==data.length) front=0;
	}
	
	public int pop() {
		if (front==back) throw new IndexOutOfBoundsException("Empty");
		int res = data[back++];
		if (back==data.length) back=0;
		return res;
	}
	
	public int size() {
		return (front+data.length-back) % data.length;
	}
	
	/*public static void main(String[] args) {
		IntQueue q=new IntQueue();
		for (int i=0; i<50; i++) {
			q.push(i);
			if ((i&1)==1) System.out.print("Pop "+q.pop());
			System.out.println(" now "+q.size()+" items");
		}
		while (q.size()>0) System.out.println("Pop "+q.pop()+" now "+q.size()+" items");
		q=new IntQueue();
		for (int i=0; i<100; i++) {
			q.push(i);
			if (i>2) System.out.print("Pop "+q.pop());
			System.out.println(" now "+q.size()+" items");
		}
	}*/
}

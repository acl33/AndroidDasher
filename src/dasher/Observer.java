package dasher;

public interface Observer<T> {
	public void HandleEvent(T t);
}

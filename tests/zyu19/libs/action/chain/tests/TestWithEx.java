package zyu19.libs.action.chain.tests;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import zyu19.libs.action.chain.ActionChain;
import zyu19.libs.action.chain.config.PureAction;
import zyu19.libs.action.chain.config.ThreadChanger;
import zyu19.libs.action.chain.config.*;
import zyu19.libs.action.chain.config.NiceConsumer;

/**
 * @version 0.4
 */
public class TestWithEx {
	BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
	ThreadPolicy threadPolicy = new ThreadPolicy(new ThreadChanger() {
		@Override
		public void runCallbackOnMainThread(Runnable runnable) {
			queue.add(runnable);
		}
	}, Executors.newCachedThreadPool());
	ActionChain chain = new ActionChain(threadPolicy);
	
	final Thread mainThread = Thread.currentThread();
	
	public boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}
	
	public Throwable getInnerMostEx(Throwable err) {
		if(err == null)
			return null;
		
		Throwable curr = err;
		while(true) {
			if(curr.getCause() == null)
				return curr;
			else curr = curr.getCause();
		}
	}
	
	@Test
	public void TestExceptionCaughtAndProcessHalted() {
		Random r = new Random();
		final Exception err = new Exception(Double.toString(r.nextDouble())); 
		final AtomicBoolean finished = new AtomicBoolean(false);
		
		new ActionChain(threadPolicy).fail(new NiceConsumer<ErrorHolder>() {
			public void consume(ErrorHolder arg) {
				final boolean testResult = isMainThread();
				queue.add(() -> Assert.assertTrue(testResult));
				Assert.assertNotNull(arg);
				Assert.assertNotNull(arg.getCause());
				Assert.assertTrue("getCause() not correct.", arg.getCause().equals(err) || (getInnerMostEx(arg.getCause()) != null ? getInnerMostEx(arg.getCause()).equals(err) : false));
				finished.set(true);
			}
		}).then(false, new PureAction<Object, Object>() {
			public Object process(Object input) throws Exception {
				throw new Exception(err);
			}
		}).then(true, new PureAction<Object, Object>() {
			public Object process(Object input) throws Exception {
				queue.add(() -> Assert.fail("ActionChain is not halted after exception is thrown (retry() not called)"));
				return null;
			}
		}).start(new NiceConsumer<Object>() {
			public void consume(Object arg) {
				Assert.fail("onSuccess is run after exception is thrown (retry() not called)");
				finished.set(true);
			}
		});
		
		// Simulate the Android Looper class
		while (!finished.get() || !queue.isEmpty())
			try {
				queue.take().run();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	}

	@Test
	public void TestJump() {
		final StringBuilder ansBuilder = new StringBuilder();
		Random random = new Random();

		class TestOutputAction implements PureAction<Object, Object> {
			public int name;

			public TestOutputAction(int name) {
				this.name = name;
			}

			public Object process(Object input) throws Exception {
				ansBuilder.append(String.valueOf(name));
				return null;
			}
		}

		// either JumpDecision or onSuccess should break the loop (see the while loop afterwards)
		final AtomicBoolean finished = new AtomicBoolean(false);

		class JumpDecision implements NiceConsumer<ErrorHolder> {

			public Integer jumpBy;

			public JumpDecision(Integer jumpTo) {this.jumpBy = jumpTo;}

			@Override
			public void consume(ErrorHolder arg) {
				Assert.assertNotNull(arg);
				if(jumpBy != null)
					arg.jumpBy(jumpBy);
				else finished.set(true);
			}

		}

		class ThrowerAction implements PureAction<Object, Object> {
			boolean hasThrownOnce = false;
			public Object process(Object input) throws Exception {
				if(!hasThrownOnce) {
					hasThrownOnce = true;
					throw new Exception();
				}
				return null;
			}
		};

		chain.clear(new NiceConsumer<ErrorHolder>() {
			public void consume(ErrorHolder arg) {
				Assert.fail(arg.getCause().toString());
			}
		});

		//----------------------------------

		String correctAns = "";
		int temp;
		boolean endWithException = false;

		ArrayList<String> prevOutputs = new ArrayList<>();

		for (int i = 0; i < 100; i++) {
			if(random.nextInt(10) >= 8) {
				if(random.nextInt(10) >= 8) {
					chain.fail(new JumpDecision(null));
					chain.then(random.nextBoolean(), new ThrowerAction());
					endWithException = true;
					break;
				}
				else if(i > 0) {
					int target = random.nextInt(i);
					chain.fail(new JumpDecision(target - i));
					chain.then(random.nextBoolean(), new ThrowerAction());
					prevOutputs.add("");
					// ensure i<=target

					int ii = i;
					if(target < ii) {
						target = ii - target;
						ii = ii - target;
						target = ii + target;
					}
                    for(int j = ii; j <= target; j++) {
                        correctAns += prevOutputs.get(j);
                    }
				}
			} else {
				temp = random.nextInt();
				correctAns += String.valueOf(temp);
				prevOutputs.add(String.valueOf(temp));
				chain.then(random.nextBoolean(), new TestOutputAction(temp));
			}
		}

		if(endWithException)
			chain.start(null);
		else {
			final int lastTest = random.nextInt();
			correctAns += String.valueOf(lastTest);

			chain.start(arg -> {
				ansBuilder.append(String.valueOf(lastTest));
				finished.set(true);
			});
		}

		// Simulate the Android Looper class
		while (!finished.get() || !queue.isEmpty())
			try {
				queue.take().run();
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		Assert.assertTrue(ansBuilder.toString() + " != " + correctAns, ansBuilder.toString().equals(correctAns));
	}

	@Test
	public void TestRetry() {
		final StringBuilder ansBuilder = new StringBuilder();
		Random random = new Random();
		
		class TestOutputAction implements PureAction<Object, Object> {
			public int name;
			
			public TestOutputAction(int name) {
				this.name = name;
			}
			
			public Object process(Object input) throws Exception {
				ansBuilder.append(String.valueOf(name));
				return null;
			}
		}
		
		// either RetryDecision or onSuccess should break the loop (see the while loop afterwards)
		final AtomicBoolean finished = new AtomicBoolean(false);
		
		class RetryDecision implements NiceConsumer<ErrorHolder> {
			
			public boolean willRetry;
			
			public RetryDecision(boolean willRetry) {this.willRetry = willRetry;}

			@Override
			public void consume(ErrorHolder arg) {
				Assert.assertNotNull(arg);
				if(willRetry)
					arg.retry();
				else finished.set(true);
			}
			
		}
		
		class ThrowerAction implements PureAction<Object, Object> {
			boolean hasThrownOnce = false;
			public Object process(Object input) throws Exception {
				if(!hasThrownOnce) {
					hasThrownOnce = true;
					throw new Exception();
				}
				return null;
			}
		};
		
		chain.clear(new NiceConsumer<ErrorHolder>() {
			public void consume(ErrorHolder arg) {
				Assert.fail(arg.getCause().toString());
			}
		});
		
		//----------------------------------
		
		String correctAns = "";
		int temp;
		boolean endWithException = false;
		
		for (int i = 0; i < 100; i++) {
			if(random.nextInt(10) >= 8) {
				if(random.nextInt(10) >= 8) {
					chain.fail(new RetryDecision(false));
					chain.then(random.nextBoolean(), new ThrowerAction());
					endWithException = true;
					break;
				}
				else {
					chain.fail(new RetryDecision(true));
					chain.then(random.nextBoolean(), new ThrowerAction());
				}
			} else {
				temp = random.nextInt();
				correctAns += String.valueOf(temp);
				chain.then(random.nextBoolean(), new TestOutputAction(temp));
			}
		}
		
		if(endWithException)
			chain.start(null);
		else {
			final int lastTest = random.nextInt();
			correctAns += String.valueOf(lastTest);
			
			chain.start(arg -> {
                ansBuilder.append(String.valueOf(lastTest));
                finished.set(true);
            });
		}
		
		// Simulate the Android Looper class
		while (!finished.get() || !queue.isEmpty())
			try {
				queue.take().run();
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		
		Assert.assertTrue(ansBuilder.toString() + " != " + correctAns, ansBuilder.toString().equals(correctAns));
	}
}

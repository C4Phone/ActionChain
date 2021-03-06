package zyu19.libs.action.chain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import zyu19.libs.action.chain.config.PureAction;
import zyu19.libs.action.chain.config.*;
import zyu19.libs.action.chain.config.NiceConsumer;

/**
 * For usages of any implementation of this class, please refer to javadoc of ChainStyle and ErrorHolder.
 * <p>
 * Users of this library can extends this class to build their own versions of ActionChain.
 * <p>
 * This class contains every piece of fundamental code. Actually the ActionChain class is
 * just an empty shell that "extends AbstractActionChain&lt;ActionChain&gt;".
 * @author Zhongzhi Yu
 *
 * @param <ThisType> The non-abstract Class that eventually extends this Abstract Class.
 * This parameter is required in order to provide method chaining.
 *  
 * @see ActionChain 
 * 
 * @version 0.3
 */
public abstract class AbstractActionChain<ThisType extends AbstractActionChain<ThisType>> implements ChainStyle<ThisType> {

	//------------- public functions (FakePromise Interface) ----------------

	@Override
	public final <In> ReadOnlyChain start(NiceConsumer<In> onSuccess) {
		ReadOnlyChain chain = new ReadOnlyChain(mActionSequence, onSuccess, mThreadPolicy);
		chain.start();
		return chain;
	}
	
	@Override
	public ThisType clear(NiceConsumer<ErrorHolder> onFailure) {
		mCurrentOnFailure = onFailure;
		mActionSequence.clear();
		return (ThisType)this;
	}

	@Override
	public final ThisType fail(NiceConsumer<ErrorHolder> onFailure) {
		mCurrentOnFailure = onFailure;
		return (ThisType)this;
	}

	@Override
	public <T extends Exception> ThisType fail(Class<T> claz, NiceConsumer<ErrorHolder<T>> onFailure) {
		final NiceConsumer<ErrorHolder> oldHandler = mCurrentOnFailure;
		mCurrentOnFailure = error -> {
			if(claz.isAssignableFrom(error.getCause().getClass()))
				onFailure.consume((ErrorHolder<T>)error);
			else {
				if(oldHandler != null)
					oldHandler.consume(error);
				else {
					ReadOnlyChain.printUncaughtEx(error.getCause());
				}
			}
		};
		return (ThisType)this;
	}

	@Override
	public <In, Out> ThisType then(boolean runOnWorkerThread, PureAction<In, Out> action) {
		mActionSequence.add(new ChainLink<In,Out>(action, mCurrentOnFailure, runOnWorkerThread));
		return (ThisType)this;
	}

	@Override
	public final <In, Out> ThisType netThen(PureAction<In, Out> action) {
		mActionSequence.add(new ChainLink<In,Out>(action, mCurrentOnFailure, true));
		return (ThisType)this;
	}

	@Override
	public final <In, Out> ThisType uiThen(PureAction<In, Out> action) {
		mActionSequence.add(new ChainLink<In,Out>(action, mCurrentOnFailure, false));
		return (ThisType)this;
	}

    @Override
    public <In> ReadOnlyChain start() {
		return start(null);
	}

    @Override
    public <Out> ThisType then(boolean runOnWorkerThread, Producer<Out> action) {
		then(runOnWorkerThread, (PureAction)in -> action.produce());
		return (ThisType)this;
	}

    @Override
    public <In> ThisType thenConsume(boolean runOnWorkerThread, Consumer<In> action) {
		then(runOnWorkerThread, (In in) -> {
			action.consume(in);
			return null;
		});
		return (ThisType)this;
	}

    @Override
    public <Out> ThisType netThen(Producer<Out> action) {
		netThen((PureAction)in -> action.produce());
		return (ThisType)this;
	}

    @Override
    public <In> ThisType netConsume(Consumer<In> action) {
		netThen((In in) -> {
			action.consume(in);
			return null;
		});
		return (ThisType)this;
	}

    @Override
    public <Out> ThisType uiThen(Producer<Out> action) {
		uiThen((PureAction)in -> action.produce());
		return (ThisType)this;
	}

    @Override
    public <In> ThisType uiConsume(Consumer<In> action) {
		uiThen((In in) -> {
			action.consume(in);
			return null;
		});
		return (ThisType)this;
	}

	//------------- Constructors ----------------

	private NiceConsumer<ErrorHolder> mCurrentOnFailure;
	private ArrayList<ChainLink<?,?>> mActionSequence = new ArrayList<>();
	protected final ThreadPolicy mThreadPolicy;
	
	public AbstractActionChain(ThreadPolicy threadPolicy) {
		mThreadPolicy = threadPolicy;
	}

	public AbstractActionChain(ThreadPolicy threadPolicy, NiceConsumer<ErrorHolder> onFailure) {
		mThreadPolicy = threadPolicy;
		mCurrentOnFailure = onFailure;
	}

	/**
	 * This constructor enables people to maintain templates of ActionChains. So that developers
	 * do not have to repeatedly type in the same code.
	 * @param threadPolicy The ThreadPolicy object that captures your platform's threading policies. See ThreadPolicy's
	 *                     Documentation and constructors for more details.
	 * @param chainTemplate a consumer that accepts an ActionChain and modifies that ActionChain
	 * @param argument the Argument to be used as the initial value in the new ActionChain's pipe
	 *                 . <p> This is equivalent to doing chain.then(obj -&gt; argument);
     */
	public AbstractActionChain(ThreadPolicy threadPolicy, NiceConsumer<ThisType> chainTemplate, Object argument) {
		mThreadPolicy = threadPolicy;
		this.netThen(() -> argument);
		chainTemplate.consume((ThisType)this);
	}


	// ------------ STATIC HELPERS ----------------

	public static Object all(Object... objects) {
		return all(Arrays.asList(objects));
	}

	/**
	 * Usage:
	 *
	 * chain.then(obj -&gt; ActionChain.all(1,2,new ActionChain(...).then(obj -&gt; 3).start())).start(ans -&gt; {
	 *     // here you will find out that ans = [1,2,3]
	 * }
	 *
	 * Note: using .all may start all actions in parallel (depending on how you started that list of ActionChains in the first place)
	 * but there may be a limit of maximum parallel thread,
	 * depending on how you instantiate the threadPolicy.
	 * @param objects if some objects in this list are created by ActionChain, this ActionChain will wait for them. Other objects will be
	 *                directly put into the list returned result
     * @return the object you should return inside the .then()
     */
	public static Object all(List<? extends Object> objects) {
		return new DotAll((List<Object>)objects);
	}
}
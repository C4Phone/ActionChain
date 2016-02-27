package zyu19.libs.action.chain.config;

import zyu19.libs.action.chain.ReadOnlyChain;

/**
 * This interface restricts ActionChain to avoid callback hell, and to enable
 * the users of this library to choose a thread for every "action" (or 'PureAction'
 * object).
 * <p>
 * Although this looks like Promise, it does NOT follow the standard of Promise-Then.
 * <p>
 * Created on 7/24/2015.
 * @author Zhongzhi Yu 
 * 
 * @version 0.3
 */
public interface ChainStyle <ThisType extends ChainStyle<?>> {
	/**
	 *  Start running the 'PureAction' objects in ChainStyle.
	 *  <p>
	 *  Your actions will be copied to another object so that
	 *  you can call clear() immediately after start().
	 * @param onSuccess if not null, it will be called after all actions finish
	 * without Exception.
	 * @return an Object representing the sequence of PureAction you created. Usually
     * this object is useless but if you return this object inside another PureAction,
     * then that PureAction will wait for this chain of actions to finish before it can finish.
	 */
	ReadOnlyChain start(Consumer<?> onSuccess);

	/**
	 * Clear all actions. You can call this function after start() so as to arrange a
	 * new sequence of actions.
	 * @param onFailure the new onFailure callback for actions added afterwards. It
	 * can also be set to null.
	 * @return this object, thus enabling method chaining.
	 */
	ThisType clear(Consumer<ErrorHolder> onFailure);

	/**
	 * set the onFailure callback for 'PureAction' objects added subsequently
	 * @param onFailure a callback to be invoked when any Exception is thrown from "actions"
	 * (or 'PureAction' objects).
	 * @return this object, thus enabling method chaining.
	 */
	ThisType fail(Consumer<ErrorHolder> onFailure);
	
	/**
	 * Add a 'PureAction' object, or an "action", in this ChainStyle.
	 * @param runOnWorkerThread if set to false, the action will run on the main thread (UI thread)
	 * specified by ThreadChanger. Otherwise the task will run on any other thread (worker thread).
	 * @param action the PureAction to be added.
	 * @param <In> The input type of this action. Retrolambda will want to set this template parameter.
	 * @param <Out> The output type of this action. Retrolambda will want to set this template parameter.
	 * @return this object, thus enabling method chaining.
	 */
	<In, Out> ThisType then(boolean runOnWorkerThread, PureAction<In, Out> action);

	
	/**
	 * Add a 'PureAction' object, or an "action", in this ChainStyle on the <strong>worker</strong> thread.
	 * @param action the PureAction to be added.
	 * @param <In> The input type of this action. Retrolambda will want to set this template parameter.
	 * @param <Out> The output type of this action. Retrolambda will want to set this template parameter.
	 * @return this object, thus enabling method chaining.
	 */
	<In, Out> ThisType netThen(PureAction<In, Out> action);


	/**
	 * Add a 'PureAction' object, or an "action", in this ChainStyle on the <strong>main (UI)</strong> thread.
	 * @param action the PureAction to be added.
	 * @param <In> The input type of this action. Retrolambda will want to set this template parameter.
	 * @param <Out> The output type of this action. Retrolambda will want to set this template parameter.
	 * @return this object, thus enabling method chaining.
	 */
	<In, Out> ThisType uiThen(PureAction<In, Out> action);
}

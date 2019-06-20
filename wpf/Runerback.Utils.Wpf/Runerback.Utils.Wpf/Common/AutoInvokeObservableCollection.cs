using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows.Threading;

namespace Runerback.Utils.Wpf
{
	public sealed class AutoInvokeObservableCollection<T> : ObservableCollection<T>
	{
		public AutoInvokeObservableCollection() : base() { }

		public AutoInvokeObservableCollection(IEnumerable<T> collection) : base(collection) { }

		public AutoInvokeObservableCollection(List<T> list) : base(list) { }
        
        public void AddRange(IEnumerable<T> collection)
        {
            if (collection == null)
                return;
            foreach (var item in collection) //CollectionView doesn't support mult-adding
                Add(item);
        }

        public override event NotifyCollectionChangedEventHandler CollectionChanged;
        
		//Telerik.Windows.Data.WeakListener<NotifyCollectionChangedEventArgs>
		private const string TelerikWeakListenerTypeName = "WeakListener`1";

        protected override void OnCollectionChanged(NotifyCollectionChangedEventArgs e)
        {
            if (reseting)
                return;

            var handler = CollectionChanged;
            if (handler == null)
                return;

            foreach (NotifyCollectionChangedEventHandler invoker in handler.GetInvocationList())
            {
                Dispatcher dispatcher = null;
                var target = invoker.Target;

                if (target is DispatcherObject dispatcherObj)
                    dispatcher = dispatcherObj.Dispatcher;
                else if (target.GetType().Name == TelerikWeakListenerTypeName)
                    dispatcher = System.Windows.Application.Current?.Dispatcher;

                if (dispatcher == null || dispatcher.CheckAccess())
                    invoker.Invoke(this, e);
                else
                    dispatcher.Invoke(
                        DispatcherPriority.DataBind,
                        (NotifyCollectionChangedEventHandler)invoker.Invoke,
                        this,
                        e);
            }
        }

        private bool reseting = false;

        public void Reset(IEnumerable<T> source)
        {
            if (source == null)
                return;

            reseting = true;
            Clear();
            foreach (var item in source)
                Add(item);

            CollectionChanged?.Invoke(
                this,
                new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
        }
	}
}

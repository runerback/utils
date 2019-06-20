using System;
using System.Windows.Input;

namespace Runerback.Utils.Wpf
{
	public sealed class DelegateCommand : ICommand
	{
		public DelegateCommand(Action<object> action, Predicate<object> predicate)
		{
			if (action == null)
				throw new ArgumentNullException("action");
			this.action = action;
			if (predicate == null)
				throw new ArgumentNullException("predicate");
			this.predicate = predicate;
		}

		private Action<object> action;
		private Predicate<object> predicate;

		public bool CanExecute(object parameter)
		{
			return predicate(parameter);
		}

		public event EventHandler CanExecuteChanged;

		public void NotifyCanExecuteChanged()
		{
			if (CanExecuteChanged != null)
			{
				CanExecuteChanged(this, EventArgs.Empty);
			}
		}

		public void Execute(object parameter)
		{
			if (CanExecute(parameter))
				action(parameter);
		}
	}
}

using System;
using System.Windows.Input;

namespace Runerback.Utils.Wpf
{
	public sealed class SimpleCommand : ICommand
	{
		public SimpleCommand(Action<object> action)
		{
			if (action == null)
				throw new ArgumentNullException("action");
			this.action = action;
		}

		private Action<object> action;

		public bool CanExecute(object parameter)
		{
			return true;
		}

		public void Execute(object parameter)
		{
			action(parameter);
		}

		//unused
		event EventHandler ICommand.CanExecuteChanged { add { } remove { } }
	}
}

using System;
using System.Windows.Input;

namespace Runerback.Utils.Wpf
{
	public sealed class RelayCommand : ICommand
	{
		public RelayCommand(Action<object> execute, Predicate<object> canExecute)
		{
			if (execute == null)
				throw new ArgumentNullException("execute");
			if (canExecute == null)
				throw new ArgumentNullException("canExecute");

			this.execute = execute;
			this.canExecute = canExecute;
		}

		private readonly Action<object> execute;
		private readonly Predicate<object> canExecute;

		public bool CanExecute(object obj)
		{
			return canExecute(obj);
		}

		public void Execute(object parameter)
		{
			execute(parameter);
		}

		public event EventHandler CanExecuteChanged
		{
			add { CommandManager.RequerySuggested += value; }
			remove { CommandManager.RequerySuggested -= value; }
		}
	}
}

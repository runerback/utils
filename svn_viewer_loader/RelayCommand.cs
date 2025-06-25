using System.Windows.Input;

namespace svn.viewer.loader;

public class RelayCommand(Action<object?> onExecute, Predicate<object?>? canExecute = default) : ICommand
{
    public bool CanExecute(object? parameter)
    {
        return canExecute?.Invoke(parameter) ?? true;
    }

    public void Execute(object? parameter)
    {
        onExecute?.Invoke(parameter);
    }

    public event EventHandler? CanExecuteChanged
    {
        add { CommandManager.RequerySuggested += value; }
        remove { CommandManager.RequerySuggested -= value; }
    }
}
using System;
using System.Collections.Generic;

namespace Runerback.Utils.AutoFac
{
    /// <summary>
    /// used for auto register in each module
    /// </summary>
    public interface IFactoryTypes
    {
        IEnumerable<Type> ServiceTypes { get; }
        void Register();
    }
}

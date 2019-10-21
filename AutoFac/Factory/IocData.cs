using System;

namespace Runerback.Utils.AutoFac
{
    internal sealed class IocData
    {
        private IocData(Type implType, Type serviceType, IoCScope scope)
        {
            if (serviceType == null)
                throw new ArgumentNullException(nameof(serviceType));
            if (!serviceType.IsInterface)
                throw new ArgumentException($"{nameof(serviceType)} should be interface");
            ImplType = implType;

            if (implType == null)
                throw new ArgumentNullException(nameof(implType));
            if (!implType.IsClass || implType.IsAbstract)
                throw new ArgumentException($"{nameof(implType)} should be non abstract class");
            ServiceType = serviceType;

            Scope = scope;
        }

        public Type ImplType { get; }
        public Type ServiceType { get; }
        public IoCScope Scope { get; }

        public static IocData Create<TDef, TImpl>(IoCScope scope) where TImpl : class
        {
            return new IocData(typeof(TImpl), typeof(TDef), scope);
        }
    }
}

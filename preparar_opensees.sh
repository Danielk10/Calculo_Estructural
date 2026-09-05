#!/bin/bash
# ==============================================================================
# Script de Preparación y Configuración de OpenSees y OpenSeesPy
# Entorno aislado con Python 3.11 para Validación Estructural Independiente
# ==============================================================================

set -e

echo "=========================================================="
echo "🏗️  PREPARANDO ENTORNO AISLADO DE OPENSEES (PYTHON 3.11)"
echo "=========================================================="

# 1. Instalar dependencias del sistema y Python 3.11 sin alterar Python del sistema
echo "📦 Paso 1: Verificando e instalando dependencias base del sistema..."
if ! command -v python3.11 >/dev/null 2>&1; then
    echo "Instalando Python 3.11 vía PPA deadsnakes..."
    sudo add-apt-repository -y ppa:deadsnakes/ppa
    sudo apt-get update
    sudo apt-get install -y python3.11 python3.11-venv python3.11-dev
fi

sudo apt-get install -y liblapack-dev libopenmpi-dev tcl-dev tk-dev libeigen3-dev >/dev/null 2>&1 || true

# 2. Configurar entorno virtual aislado en ~/opensees-env
ENV_DIR="$HOME/opensees-env"
echo "🐍 Paso 2: Configurando entorno virtual en $ENV_DIR..."

if [ ! -d "$ENV_DIR" ] || [ ! -x "$ENV_DIR/bin/python" ]; then
    python3.11 -m venv --clear "$ENV_DIR"
fi

# Activar entorno e instalar paquetes
source "$ENV_DIR/bin/activate"
pip install --upgrade pip >/dev/null 2>&1
pip install openseespy numpy scipy matplotlib >/dev/null 2>&1

# 3. Test de verificación
echo "🧪 Paso 3: Verificando instalación de OpenSeesPy..."
python -c "import openseespy.opensees as ops; ops.wipe(); ops.model('basic', '-ndm', 2, '-ndf', 2); print('✅ OpenSeesPy inicializado correctamente')"

echo "=========================================================="
echo "🎉 ¡OpenSees y OpenSeesPy configurados exitosamente!"
echo ""
echo "Para activar el entorno y ejecutar análisis de validación:"
echo "  source ~/opensees-env/bin/activate"
echo "  python validate_with_opensees.py"
echo "=========================================================="

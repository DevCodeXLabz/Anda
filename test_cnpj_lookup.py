#!/usr/bin/env python3
"""
CNPJ Lookup Validation Test Script

This script tests the Anda app's CNPJ lookup functionality using the BrasilAPI.
It validates that the provided CNPJ (20.074.884/0001-36) correctly returns
"Nancy Rezende de Lima" as the legal name.
"""

import requests
import json
import sys

def normalize_cnpj(cnpj: str) -> str:
    """Remove non-digit characters from CNPJ."""
    return ''.join(filter(str.isdigit, cnpj))

def test_cnpj_lookup(cnpj: str) -> dict:
    """
    Test CNPJ lookup against BrasilAPI.
    
    Args:
        cnpj: CNPJ string (with or without formatting)
    
    Returns:
        Dictionary with lookup results
    """
    normalized = normalize_cnpj(cnpj)
    
    if len(normalized) != 14:
        return {
            'success': False,
            'error': f'CNPJ must have 14 digits, got {len(normalized)}'
        }
    
    endpoint = f'https://brasilapi.com.br/api/cnpj/v1/{normalized}'
    
    try:
        response = requests.get(endpoint, timeout=10)
        
        if response.status_code not in (200, 299):
            return {
                'success': False,
                'error': f'API returned HTTP {response.status_code}'
            }
        
        data = response.json()
        
        result = {
            'success': True,
            'cnpj': normalized,
            'legal_name': data.get('razao_social', ''),
            'trade_name': data.get('nome_fantasia', ''),
            'cnae': data.get('cnae_fiscal', ''),
            'city': data.get('municipio', ''),
            'state': data.get('uf', ''),
            'postal_code': data.get('cep', '')
        }
        
        return result
        
    except requests.exceptions.RequestException as e:
        return {
            'success': False,
            'error': f'Network error: {str(e)}'
        }
    except json.JSONDecodeError as e:
        return {
            'success': False,
            'error': f'Invalid JSON response: {str(e)}'
        }

def validate_nancy_rezende(result: dict) -> bool:
    """Validate that the result matches Nancy Rezende de Lima's company."""
    if not result.get('success'):
        print(f"❌ FAILED: API call failed - {result.get('error')}")
        return False
    
    expected_name_fragment = "nancy rezende de lima"
    actual_legal_name = result.get('legal_name', '').strip().lower()
    
    # The API may return the name with extra numbers (e.g., "NANCY REZENDE DE LIMA 03852515998")
    # We check if the name contains the expected fragment
    if expected_name_fragment not in actual_legal_name:
        print(f"❌ FAILED: Legal name mismatch")
        print(f"   Expected to contain: {expected_name_fragment}")
        print(f"   Got: {actual_legal_name.upper()}")
        return False
    
    if not result.get('city'):
        print(f"⚠️  WARNING: City is empty")
        return False
    
    if not result.get('state'):
        print(f"⚠️  WARNING: State is empty")
        return False
    
    print(f"✅ PASSED: CNPJ lookup successful")
    return True

def main():
    """Run the CNPJ lookup test."""
    test_cnpj = "20.074.884/0001-36"
    
    print("=" * 70)
    print("ANDA APP - CNPJ LOOKUP VALIDATION TEST")
    print("=" * 70)
    print(f"\nTest CNPJ: {test_cnpj}")
    print(f"Expected Legal Name: Nancy Rezende de Lima")
    print("\nFetching company data from BrasilAPI...\n")
    
    result = test_cnpj_lookup(test_cnpj)
    
    if result.get('success'):
        print(f"📍 Results from BrasilAPI:")
        print(f"   CNPJ:             {result['cnpj']}")
        print(f"   Legal Name:       {result['legal_name']}")
        print(f"   Trade Name:       {result['trade_name']}")
        print(f"   City:             {result['city']}")
        print(f"   State:            {result['state']}")
        print(f"   CNAE:             {result['cnae']}")
        print(f"   Postal Code:      {result['postal_code']}")
        print()
        
        success = validate_nancy_rezende(result)
        print("\n" + "=" * 70)
        
        if success:
            print("✅ TEST RESULT: PASSED - CNPJ lookup is working correctly!")
            print("=" * 70)
            return 0
        else:
            print("❌ TEST RESULT: FAILED - Data validation failed!")
            print("=" * 70)
            return 1
    else:
        print(f"❌ API Error: {result.get('error')}")
        print("\n" + "=" * 70)
        print("❌ TEST RESULT: FAILED - Could not fetch data from API!")
        print("=" * 70)
        return 1

if __name__ == "__main__":
    sys.exit(main())

